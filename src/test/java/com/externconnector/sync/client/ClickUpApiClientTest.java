package com.externconnector.sync.client;

import com.externconnector.sync.exception.SyncException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

class ClickUpApiClientTest {

    private static MockWebServer mockServer;
    private ClickUpApiClient client;

    @BeforeAll
    static void startServer() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();
    }

    @AfterAll
    static void stopServer() throws IOException {
        mockServer.shutdown();
    }

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        WebClient webClient = WebClient.builder()
                .baseUrl(mockServer.url("/").toString())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("Authorization", "test-key")
                .build();

        client = new ClickUpApiClient(webClient, objectMapper);
    }

    @Test
    void updateTaskStatus_success_noException() {
        mockServer.enqueue(new MockResponse()
                .setBody("{\"id\":\"task1\",\"name\":\"Test\",\"status\":{\"status\":\"complete\"},\"list\":{\"id\":\"list1\"}}")
                .addHeader("Content-Type", "application/json")
                .setResponseCode(200));

        assertThatNoException().isThrownBy(() ->
                client.updateTaskStatus("task1", "complete"));
    }

    @Test
    void updateTaskStatus_apiError_throwsSyncException() {
        mockServer.enqueue(new MockResponse()
                .setBody("{\"err\":\"Status does not exist\"}")
                .addHeader("Content-Type", "application/json")
                .setResponseCode(200));

        assertThatThrownBy(() -> client.updateTaskStatus("task1", "invalid-status"))
                .isInstanceOf(SyncException.class)
                .hasMessageContaining("Status does not exist");
    }

    @Test
    void getTask_success_returnsParsedTask() {
        mockServer.enqueue(new MockResponse()
                .setBody("{\"id\":\"task1\",\"name\":\"My Task\",\"status\":{\"status\":\"in progress\"},\"list\":{\"id\":\"list1\"}}")
                .addHeader("Content-Type", "application/json"));

        Optional<ClickUpApiClient.ClickUpTaskDetails> result = client.getTask("task1");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("task1");
        assertThat(result.get().status()).isEqualTo("in progress");
        assertThat(result.get().listId()).isEqualTo("list1");
    }

    @Test
    void getTask_notFound_returnsEmpty() {
        mockServer.enqueue(new MockResponse()
                .setBody("{\"err\":\"Team not authorized\"}")
                .addHeader("Content-Type", "application/json")
                .setResponseCode(200));

        Optional<ClickUpApiClient.ClickUpTaskDetails> result = client.getTask("missing");
        assertThat(result).isEmpty();
    }

    @Test
    void getListStatuses_parsesAndCaches() {
        mockServer.enqueue(new MockResponse()
                .setBody("{\"id\":\"list-unique-cache\",\"statuses\":[{\"status\":\"to do\"},{\"status\":\"in progress\"},{\"status\":\"complete\"}]}")
                .addHeader("Content-Type", "application/json"));

        int before = mockServer.getRequestCount();
        List<String> statuses = client.getListStatuses("list-unique-cache");
        List<String> cached   = client.getListStatuses("list-unique-cache"); // should be cached

        assertThat(statuses).containsExactlyInAnyOrder("to do", "in progress", "complete");
        assertThat(mockServer.getRequestCount()).isEqualTo(before + 1); // only 1 new request
        assertThat(cached).isEqualTo(statuses);
    }

    @Test
    void createTask_success_returnsParsedTask() {
        mockServer.enqueue(new MockResponse()
                .setBody("{\"id\":\"new-task\",\"name\":\"My Task\",\"status\":{\"status\":\"to do\"},\"list\":{\"id\":\"list1\"}}")
                .addHeader("Content-Type", "application/json")
                .setResponseCode(200));

        ClickUpApiClient.ClickUpTaskDetails result = client.createTask("list1", "My Task", "desc", "to do");

        assertThat(result.id()).isEqualTo("new-task");
        assertThat(result.name()).isEqualTo("My Task");
    }
}
