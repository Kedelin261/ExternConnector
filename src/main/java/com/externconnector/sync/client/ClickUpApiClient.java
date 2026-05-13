package com.externconnector.sync.client;

import com.externconnector.sync.exception.NonRetryableException;
import com.externconnector.sync.exception.SyncException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ClickUp REST API v2 client.
 * Handles task status updates and task lookups.
 * Ref: https://clickup.com/api/clickupreference/operation/UpdateTask/
 */
@Component
public class ClickUpApiClient {

    private static final Logger log = LoggerFactory.getLogger(ClickUpApiClient.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    // Cache available statuses per list to reduce API calls
    // key: listId, value: list of status names (lowercase)
    private final ConcurrentHashMap<String, List<String>> statusCache = new ConcurrentHashMap<>();

    public ClickUpApiClient(
            @Qualifier("clickUpWebClient") WebClient webClient,
            ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Update a ClickUp task's status.
     * PUT /task/{task_id}  body: { "status": "<status>" }
     */
    @Retry(name = "clickup")
    @CircuitBreaker(name = "clickup")
    public void updateTaskStatus(String taskId, String newStatus) {
        log.info("Updating ClickUp task {} to status '{}'", taskId, newStatus);

        Map<String, String> body = Map.of("status", newStatus);

        try {
            JsonNode response = webClient.put()
                    .uri("/task/{taskId}", taskId)
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() && status != HttpStatus.TOO_MANY_REQUESTS,
                            resp -> resp.bodyToMono(String.class).map(b -> {
                                log.error("ClickUp 4xx updating task {}: {}", taskId, b);
                                return new NonRetryableException("ClickUp API 4xx: " + resp.statusCode() + " - " + b);
                            }))
                    .onStatus(HttpStatus.TOO_MANY_REQUESTS::equals,
                            resp -> {
                                String retryAfter = resp.headers().header("Retry-After").stream().findFirst().orElse("60");
                                log.warn("ClickUp rate limit hit, Retry-After: {}s", retryAfter);
                                return resp.bodyToMono(String.class).map(b ->
                                        new SyncException("ClickUp rate limit exceeded. Retry after " + retryAfter + "s"));
                            })
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response != null && response.has("err")) {
                String err = response.path("err").asText();
                log.error("ClickUp API error updating task {}: {}", taskId, err);
                throw new SyncException("ClickUp error: " + err);
            }

            log.info("Successfully updated ClickUp task {} to status '{}'", taskId, newStatus);

        } catch (WebClientResponseException e) {
            log.error("ClickUp API error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            if (e.getStatusCode().is4xxClientError() && e.getStatusCode() != HttpStatus.TOO_MANY_REQUESTS) {
                throw new NonRetryableException("ClickUp client error: " + e.getMessage(), e);
            }
            throw new SyncException("ClickUp API error: " + e.getMessage(), e);
        }
    }

    /**
     * Fetch task details from ClickUp.
     * GET /task/{task_id}
     */
    @Retry(name = "clickup")
    @CircuitBreaker(name = "clickup")
    public Optional<ClickUpTaskDetails> getTask(String taskId) {
        log.debug("Fetching ClickUp task {}", taskId);

        try {
            JsonNode response = webClient.get()
                    .uri("/task/{taskId}", taskId)
                    .retrieve()
                    .onStatus(HttpStatus.NOT_FOUND::equals,
                            resp -> resp.bodyToMono(String.class).map(b ->
                                    new NonRetryableException("ClickUp task not found: " + taskId)))
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || response.isMissingNode()) {
                return Optional.empty();
            }

            if (response.has("err")) {
                log.warn("ClickUp task {} not found or error: {}", taskId, response.path("err").asText());
                return Optional.empty();
            }

            String id = response.path("id").asText();
            String name = response.path("name").asText();
            String status = response.path("status").path("status").asText();
            String listId = response.path("list").path("id").asText();

            return Optional.of(new ClickUpTaskDetails(id, name, status, listId));

        } catch (NonRetryableException e) {
            return Optional.empty();
        } catch (WebClientResponseException e) {
            log.error("ClickUp API error fetching task {}: {}", taskId, e.getMessage());
            throw new SyncException("Failed to fetch ClickUp task: " + taskId, e);
        }
    }

    /**
     * Get available statuses for a list.
     * GET /list/{list_id}
     */
    @Retry(name = "clickup")
    @CircuitBreaker(name = "clickup")
    public List<String> getListStatuses(String listId) {
        return statusCache.computeIfAbsent(listId, this::fetchListStatuses);
    }

    /**
     * Create a task in ClickUp.
     * POST /list/{list_id}/task
     */
    @Retry(name = "clickup")
    @CircuitBreaker(name = "clickup")
    public ClickUpTaskDetails createTask(String listId, String name, String description, String status) {
        log.info("Creating ClickUp task '{}' in list {}", name, listId);

        Map<String, Object> body;
        if (status != null && !status.isBlank()) {
            body = Map.of("name", name, "description", description != null ? description : "", "status", status);
        } else {
            body = Map.of("name", name, "description", description != null ? description : "");
        }

        try {
            JsonNode response = webClient.post()
                    .uri("/list/{listId}/task", listId)
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(status2 -> status2.is4xxClientError(),
                            resp -> resp.bodyToMono(String.class).map(b ->
                                    new NonRetryableException("ClickUp create task error: " + b)))
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null) {
                throw new SyncException("Null response creating ClickUp task");
            }

            String taskId = response.path("id").asText();
            String taskName = response.path("name").asText();
            String taskStatus = response.path("status").path("status").asText();
            String taskListId = response.path("list").path("id").asText(listId);

            log.info("Created ClickUp task {} in list {}", taskId, listId);
            return new ClickUpTaskDetails(taskId, taskName, taskStatus, taskListId);

        } catch (WebClientResponseException e) {
            log.error("ClickUp create task error: {}", e.getMessage());
            throw new SyncException("Failed to create ClickUp task: " + e.getMessage(), e);
        }
    }

    /**
     * Evict status cache for a list.
     */
    public void evictStatusCache(String listId) {
        statusCache.remove(listId);
        log.debug("Evicted ClickUp status cache for list {}", listId);
    }

    // --- private helpers ---

    private List<String> fetchListStatuses(String listId) {
        log.debug("Fetching ClickUp statuses for list {}", listId);

        try {
            JsonNode response = webClient.get()
                    .uri("/list/{listId}", listId)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.has("statuses")) {
                log.warn("No statuses found for ClickUp list {}", listId);
                return List.of();
            }

            List<String> statuses = new java.util.ArrayList<>();
            response.path("statuses").forEach(s -> {
                String statusName = s.path("status").asText();
                if (!statusName.isBlank()) {
                    statuses.add(statusName.toLowerCase());
                }
            });

            log.debug("Found {} statuses for ClickUp list {}: {}", statuses.size(), listId, statuses);
            return List.copyOf(statuses);

        } catch (WebClientResponseException e) {
            log.error("Error fetching ClickUp list statuses for {}: {}", listId, e.getMessage());
            return List.of();
        }
    }

    public record ClickUpTaskDetails(
            String id,
            String name,
            String status,
            String listId
    ) {}
}
