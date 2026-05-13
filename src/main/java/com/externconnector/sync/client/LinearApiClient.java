package com.externconnector.sync.client;

import com.externconnector.sync.exception.NonRetryableException;
import com.externconnector.sync.exception.SyncException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Linear GraphQL API client.
 * Handles issue status updates and state lookups.
 * Ref: https://developers.linear.app/docs/graphql/working-with-the-graphql-api
 */
@Component
public class LinearApiClient {

    private static final Logger log = LoggerFactory.getLogger(LinearApiClient.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    // Cache workflow states per team to reduce API calls
    // key: teamId, value: map of stateName -> stateId
    private final ConcurrentHashMap<String, Map<String, String>> stateCache = new ConcurrentHashMap<>();

    public LinearApiClient(
            @Qualifier("linearWebClient") WebClient webClient,
            ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Update Linear issue state.
     * Uses GraphQL mutation: issueUpdate(id, input: { stateId })
     */
    @Retry(name = "linear")
    @CircuitBreaker(name = "linear")
    public void updateIssueStatus(String issueId, String stateName, String teamId) {
        log.info("Updating Linear issue {} to status '{}' (team: {})", issueId, stateName, teamId);

        String stateId = resolveStateId(stateName, teamId);

        String mutation = """
            mutation IssueUpdate($issueId: String!, $stateId: String!) {
              issueUpdate(id: $issueId, input: { stateId: $stateId }) {
                success
                issue {
                  id
                  state {
                    name
                  }
                }
              }
            }
        """;

        ObjectNode variables = objectMapper.createObjectNode()
                .put("issueId", issueId)
                .put("stateId", stateId);

        ObjectNode body = objectMapper.createObjectNode()
                .put("query", mutation);
        body.set("variables", variables);

        try {
            JsonNode response = webClient.post()
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() && status != HttpStatus.TOO_MANY_REQUESTS,
                            resp -> resp.bodyToMono(String.class).map(b -> {
                                log.error("Linear 4xx error: {}", b);
                                return new NonRetryableException("Linear API 4xx: " + resp.statusCode());
                            }))
                    .onStatus(HttpStatus.TOO_MANY_REQUESTS::equals,
                            resp -> resp.bodyToMono(String.class).map(b ->
                                    new SyncException("Linear rate limit exceeded")))
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null) {
                throw new SyncException("Null response from Linear API");
            }

            // Check for GraphQL errors
            if (response.has("errors")) {
                String errors = response.get("errors").toString();
                log.error("Linear GraphQL errors: {}", errors);
                throw new SyncException("Linear GraphQL error: " + errors);
            }

            JsonNode issueUpdate = response.path("data").path("issueUpdate");
            boolean success = issueUpdate.path("success").asBoolean(false);
            if (!success) {
                throw new SyncException("Linear issueUpdate returned success=false for issue " + issueId);
            }

            log.info("Successfully updated Linear issue {} to state '{}'", issueId, stateName);

        } catch (WebClientResponseException e) {
            log.error("Linear API error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            if (e.getStatusCode().is4xxClientError() && e.getStatusCode() != HttpStatus.TOO_MANY_REQUESTS) {
                throw new NonRetryableException("Linear client error: " + e.getMessage(), e);
            }
            throw new SyncException("Linear API error: " + e.getMessage(), e);
        }
    }

    /**
     * Fetch issue details from Linear.
     */
    @Retry(name = "linear")
    @CircuitBreaker(name = "linear")
    public Optional<LinearIssueDetails> getIssue(String issueId) {
        String query = """
            query GetIssue($id: String!) {
              issue(id: $id) {
                id
                title
                description
                identifier
                url
                state {
                  id
                  name
                  type
                }
                team {
                  id
                  name
                  key
                }
              }
            }
        """;

        ObjectNode variables = objectMapper.createObjectNode().put("id", issueId);
        ObjectNode body = objectMapper.createObjectNode().put("query", query);
        body.set("variables", variables);

        try {
            JsonNode response = webClient.post()
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.has("data")) {
                return Optional.empty();
            }

            JsonNode issueNode = response.path("data").path("issue");
            if (issueNode.isMissingNode() || issueNode.isNull()) {
                return Optional.empty();
            }

            return Optional.of(new LinearIssueDetails(
                    issueNode.path("id").asText(),
                    issueNode.path("title").asText(),
                    issueNode.path("identifier").asText(),
                    issueNode.path("state").path("name").asText(),
                    issueNode.path("team").path("id").asText()
            ));

        } catch (WebClientResponseException e) {
            log.error("Linear API error fetching issue {}: {}", issueId, e.getMessage());
            throw new SyncException("Failed to fetch Linear issue: " + issueId, e);
        }
    }

    /**
     * Fetch workflow states for a team and cache them.
     * Returns map: stateName -> stateId
     */
    @Retry(name = "linear")
    @CircuitBreaker(name = "linear")
    public Map<String, String> getWorkflowStates(String teamId) {
        return stateCache.computeIfAbsent(teamId, this::fetchWorkflowStates);
    }

    /**
     * Evict state cache for a team (e.g. on mapping changes).
     */
    public void evictStateCache(String teamId) {
        stateCache.remove(teamId);
        log.debug("Evicted Linear state cache for team {}", teamId);
    }

    /**
     * Evict entire state cache.
     */
    public void evictAllStateCaches() {
        stateCache.clear();
        log.debug("Cleared entire Linear state cache");
    }

    // --- private helpers ---

    private String resolveStateId(String stateName, String teamId) {
        Map<String, String> states = getWorkflowStates(teamId);
        String stateId = states.get(stateName);
        if (stateId == null) {
            // Try case-insensitive match
            stateId = states.entrySet().stream()
                    .filter(e -> e.getKey().equalsIgnoreCase(stateName))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);
        }
        if (stateId == null) {
            // Evict cache and retry once
            evictStateCache(teamId);
            Map<String, String> freshStates = fetchWorkflowStates(teamId);
            stateCache.put(teamId, freshStates);
            stateId = freshStates.get(stateName);
            if (stateId == null) {
                stateId = freshStates.entrySet().stream()
                        .filter(e -> e.getKey().equalsIgnoreCase(stateName))
                        .map(Map.Entry::getValue)
                        .findFirst()
                        .orElse(null);
            }
        }
        if (stateId == null) {
            throw new NonRetryableException(
                    "Linear state '" + stateName + "' not found for team " + teamId +
                    ". Available: " + getWorkflowStates(teamId).keySet());
        }
        return stateId;
    }

    private Map<String, String> fetchWorkflowStates(String teamId) {
        log.debug("Fetching Linear workflow states for team {}", teamId);
        String query = """
            query GetWorkflowStates($teamId: String!) {
              workflowStates(filter: { team: { id: { eq: $teamId } } }) {
                nodes {
                  id
                  name
                  type
                }
              }
            }
        """;

        ObjectNode variables = objectMapper.createObjectNode().put("teamId", teamId);
        ObjectNode body = objectMapper.createObjectNode().put("query", query);
        body.set("variables", variables);

        JsonNode response = webClient.post()
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        Map<String, String> result = new ConcurrentHashMap<>();
        if (response != null && response.has("data")) {
            JsonNode nodes = response.path("data").path("workflowStates").path("nodes");
            if (nodes.isArray()) {
                nodes.forEach(n -> {
                    String name = n.path("name").asText();
                    String id   = n.path("id").asText();
                    if (!name.isBlank() && !id.isBlank()) {
                        result.put(name, id);
                    }
                });
            }
        }

        log.debug("Fetched {} workflow states for team {}", result.size(), teamId);
        return result;
    }

    public record LinearIssueDetails(
            String id,
            String title,
            String identifier,
            String stateName,
            String teamId
    ) {}
}
