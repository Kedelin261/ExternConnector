package com.externconnector.sync.client;

import com.externconnector.sync.exception.NonRetryableException;
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
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

class LinearApiClientTest {

    private static MockWebServer mockServer;
    private LinearApiClient client;
    private ObjectMapper objectMapper;

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
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        WebClient webClient = WebClient.builder()
                .baseUrl(mockServer.url("/").toString())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("Authorization", "test-key")
                .build();

        client = new LinearApiClient(webClient, objectMapper);
    }

    @Test
    void getWorkflowStates_parsesCorrectly() {
        mockServer.enqueue(new MockResponse()
                .setBody("""
                    {
                      "data": {
                        "workflowStates": {
                          "nodes": [
                            {"id": "s1", "name": "Todo", "type": "unstarted"},
                            {"id": "s2", "name": "In Progress", "type": "started"},
                            {"id": "s3", "name": "Done", "type": "completed"}
                          ]
                        }
                      }
                    }
                    """)
                .addHeader("Content-Type", "application/json"));

        Map<String, String> states = client.getWorkflowStates("team1");

        assertThat(states).containsKeys("Todo", "In Progress", "Done");
        assertThat(states.get("Todo")).isEqualTo("s1");
        assertThat(states.get("Done")).isEqualTo("s3");
    }

    @Test
    void getWorkflowStates_isCached() {
        mockServer.enqueue(new MockResponse()
                .setBody("{\"data\":{\"workflowStates\":{\"nodes\":[{\"id\":\"s1\",\"name\":\"Done\",\"type\":\"completed\"}]}}}")
                .addHeader("Content-Type", "application/json"));

        int before = mockServer.getRequestCount();
        // First call fetches from server
        client.getWorkflowStates("team-cached-unique");
        // Second call uses cache — no additional request
        client.getWorkflowStates("team-cached-unique");

        assertThat(mockServer.getRequestCount()).isEqualTo(before + 1);
    }

    @Test
    void evictStateCache_allowsRefetch() {
        mockServer.enqueue(new MockResponse()
                .setBody("{\"data\":{\"workflowStates\":{\"nodes\":[{\"id\":\"s1\",\"name\":\"Done\",\"type\":\"completed\"}]}}}")
                .addHeader("Content-Type", "application/json"));
        mockServer.enqueue(new MockResponse()
                .setBody("{\"data\":{\"workflowStates\":{\"nodes\":[{\"id\":\"s1\",\"name\":\"Done\",\"type\":\"completed\"},{\"id\":\"s2\",\"name\":\"Archive\",\"type\":\"cancelled\"}]}}}")
                .addHeader("Content-Type", "application/json"));

        int before = mockServer.getRequestCount();
        client.getWorkflowStates("team-evict");
        client.evictStateCache("team-evict");
        client.getWorkflowStates("team-evict");

        assertThat(mockServer.getRequestCount()).isEqualTo(before + 2);
    }

    @Test
    void getIssue_notFound_returnsEmpty() {
        mockServer.enqueue(new MockResponse()
                .setBody("{\"data\":{\"issue\":null}}")
                .addHeader("Content-Type", "application/json"));

        Optional<LinearApiClient.LinearIssueDetails> result = client.getIssue("missing");
        assertThat(result).isEmpty();
    }

    @Test
    void getIssue_success_returnsParsedDetails() {
        mockServer.enqueue(new MockResponse()
                .setBody("""
                    {
                      "data": {
                        "issue": {
                          "id": "issue-1",
                          "title": "Test Issue",
                          "identifier": "ENG-123",
                          "state": {"id": "s1", "name": "In Progress", "type": "started"},
                          "team": {"id": "team1", "name": "Engineering", "key": "ENG"}
                        }
                      }
                    }
                    """)
                .addHeader("Content-Type", "application/json"));

        Optional<LinearApiClient.LinearIssueDetails> result = client.getIssue("issue-1");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("issue-1");
        assertThat(result.get().stateName()).isEqualTo("In Progress");
        assertThat(result.get().teamId()).isEqualTo("team1");
    }

    @Test
    void updateIssueStatus_graphqlError_throwsSyncException() {
        // Enqueue state fetch + update
        mockServer.enqueue(new MockResponse()
                .setBody("{\"data\":{\"workflowStates\":{\"nodes\":[{\"id\":\"s3\",\"name\":\"Done\",\"type\":\"completed\"}]}}}")
                .addHeader("Content-Type", "application/json"));
        mockServer.enqueue(new MockResponse()
                .setBody("{\"errors\":[{\"message\":\"Not authorized\"}]}")
                .addHeader("Content-Type", "application/json"));

        assertThatThrownBy(() -> client.updateIssueStatus("issue-1", "Done", "team1"))
                .isInstanceOf(SyncException.class)
                .hasMessageContaining("GraphQL error");
    }

    @Test
    void updateIssueStatus_success_returnsNormally() {
        mockServer.enqueue(new MockResponse()
                .setBody("{\"data\":{\"workflowStates\":{\"nodes\":[{\"id\":\"s3\",\"name\":\"Done\",\"type\":\"completed\"}]}}}")
                .addHeader("Content-Type", "application/json"));
        mockServer.enqueue(new MockResponse()
                .setBody("{\"data\":{\"issueUpdate\":{\"success\":true,\"issue\":{\"id\":\"issue-1\",\"state\":{\"name\":\"Done\"}}}}}")
                .addHeader("Content-Type", "application/json"));

        assertThatNoException().isThrownBy(() ->
                client.updateIssueStatus("issue-1", "Done", "team1"));
    }
}
