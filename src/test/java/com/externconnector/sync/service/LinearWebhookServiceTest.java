package com.externconnector.sync.service;

import com.externconnector.sync.client.ClickUpApiClient;
import com.externconnector.sync.config.LinearProperties;
import com.externconnector.sync.entity.SyncEvent;
import com.externconnector.sync.entity.TaskMapping;
import com.externconnector.sync.entity.WebhookLog;
import com.externconnector.sync.exception.WebhookAuthException;
import com.externconnector.sync.security.WebhookSignatureVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LinearWebhookServiceTest {

    @Mock private WebhookSignatureVerifier signatureVerifier;
    @Mock private LinearProperties linearProperties;
    @Mock private LinearProperties.Api linearApi; // used via lenient stubbing in setUp
    @Mock private TaskMappingService taskMappingService;
    @Mock private StatusMappingService statusMappingService;
    @Mock private SyncLoopPreventionService loopPrevention;
    @Mock private IdempotencyService idempotencyService;
    @Mock private WebhookAuditService auditService;
    @Mock private ClickUpApiClient clickUpApiClient;

    private LinearWebhookService service;
    private ObjectMapper objectMapper;

    private static final String ISSUE_UPDATE_PAYLOAD = """
        {
          "action": "update",
          "type": "Issue",
          "webhookId": "wh1",
          "webhookTimestamp": 1700000000000,
          "data": {
            "id": "issue-abc",
            "title": "Test Issue",
            "state": {"id": "state1", "name": "Done", "type": "completed"},
            "team": {"id": "team1", "name": "Eng", "key": "ENG"}
          }
        }
        """;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        com.fasterxml.jackson.datatype.jsr310.JavaTimeModule jtm = new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule();
        objectMapper.registerModule(jtm);
        when(linearProperties.webhookSecret()).thenReturn("test-secret");
        // lenient — not all tests call methods that require the api() sub-stub
        when(linearProperties.api()).thenReturn(linearApi);

        service = new LinearWebhookService(
                signatureVerifier, linearProperties,
                taskMappingService, statusMappingService,
                loopPrevention, idempotencyService, auditService,
                clickUpApiClient, objectMapper);
    }

    @Test
    void handle_invalidSignature_throwsWebhookAuthException() {
        doThrow(new WebhookAuthException("Invalid Linear webhook signature"))
                .when(signatureVerifier).verifyLinear(anyString(), anyString(), anyString());

        assertThatThrownBy(() -> service.handle(ISSUE_UPDATE_PAYLOAD, "badsig"))
                .isInstanceOf(WebhookAuthException.class);

        verifyNoInteractions(taskMappingService, clickUpApiClient);
    }

    @Test
    void handle_duplicateEvent_skipsProcessing() {
        WebhookLog mockLog = WebhookLog.builder().id(1L).build();
        when(auditService.logWebhook(anyString(), any(), anyString(), any(), anyBoolean()))
                .thenReturn(mockLog);
        when(idempotencyService.alreadyProcessed(anyString(), any())).thenReturn(true);

        service.handle(ISSUE_UPDATE_PAYLOAD, "sig");

        verifyNoInteractions(taskMappingService, clickUpApiClient);
        verify(auditService).markWebhookProcessed(1L, true, "DUPLICATE");
    }

    @Test
    void handle_loopPreventionTriggered_skipsSync() {
        WebhookLog mockLog = WebhookLog.builder().id(1L).build();
        when(auditService.logWebhook(anyString(), any(), anyString(), any(), anyBoolean()))
                .thenReturn(mockLog);
        when(idempotencyService.alreadyProcessed(anyString(), any())).thenReturn(false);
        when(loopPrevention.shouldSkip("LINEAR", "issue-abc", "Done")).thenReturn(true);

        service.handle(ISSUE_UPDATE_PAYLOAD, "sig");

        verifyNoInteractions(clickUpApiClient);
        verify(auditService).recordSyncEvent(
                isNull(), eq(WebhookLog.Platform.LINEAR), eq(WebhookLog.Platform.CLICKUP),
                anyString(), eq("Done"), isNull(),
                eq(SyncEvent.SyncStatus.LOOP_PREVENTED), isNull(), anyString());
    }

    @Test
    void handle_noTaskMapping_skipsSync() {
        WebhookLog mockLog = WebhookLog.builder().id(1L).build();
        when(auditService.logWebhook(anyString(), any(), anyString(), any(), anyBoolean()))
                .thenReturn(mockLog);
        when(idempotencyService.alreadyProcessed(anyString(), any())).thenReturn(false);
        when(loopPrevention.shouldSkip(anyString(), anyString(), anyString())).thenReturn(false);
        when(taskMappingService.findByLinearIssueId("issue-abc")).thenReturn(Optional.empty());

        service.handle(ISSUE_UPDATE_PAYLOAD, "sig");

        verifyNoInteractions(clickUpApiClient);
    }

    @Test
    void handle_noStatusMapping_recordsSkipped() {
        WebhookLog mockLog = WebhookLog.builder().id(1L).build();
        TaskMapping mapping = TaskMapping.builder()
                .id(1L).linearIssueId("issue-abc").clickupTaskId("cu1").build();

        when(auditService.logWebhook(anyString(), any(), anyString(), any(), anyBoolean()))
                .thenReturn(mockLog);
        when(idempotencyService.alreadyProcessed(anyString(), any())).thenReturn(false);
        when(loopPrevention.shouldSkip(anyString(), anyString(), anyString())).thenReturn(false);
        when(taskMappingService.findByLinearIssueId("issue-abc")).thenReturn(Optional.of(mapping));
        when(statusMappingService.toClickUpStatus("Done")).thenReturn(Optional.empty());

        service.handle(ISSUE_UPDATE_PAYLOAD, "sig");

        verifyNoInteractions(clickUpApiClient);
        verify(auditService).recordSyncEvent(
                eq(mapping), any(), any(), anyString(), eq("Done"), isNull(),
                eq(SyncEvent.SyncStatus.SKIPPED), anyString(), anyString());
    }

    @Test
    void handle_successfulSync_updatesClickUp() {
        WebhookLog mockLog = WebhookLog.builder().id(1L).build();
        TaskMapping mapping = TaskMapping.builder()
                .id(1L).linearIssueId("issue-abc").clickupTaskId("cu1").linearTeamId("team1").build();

        when(auditService.logWebhook(anyString(), any(), anyString(), any(), anyBoolean()))
                .thenReturn(mockLog);
        when(idempotencyService.alreadyProcessed(anyString(), any())).thenReturn(false);
        when(loopPrevention.shouldSkip(anyString(), anyString(), anyString())).thenReturn(false);
        when(taskMappingService.findByLinearIssueId("issue-abc")).thenReturn(Optional.of(mapping));
        when(statusMappingService.toClickUpStatus("Done")).thenReturn(Optional.of("complete"));
        doNothing().when(clickUpApiClient).updateTaskStatus("cu1", "complete");

        service.handle(ISSUE_UPDATE_PAYLOAD, "sig");

        verify(loopPrevention).registerSyncOrigin("CLICKUP", "cu1", "complete");
        verify(clickUpApiClient).updateTaskStatus("cu1", "complete");
        verify(taskMappingService).updateStatuses(1L, "Done", "complete");
        verify(auditService).recordSyncEvent(
                eq(mapping), eq(WebhookLog.Platform.LINEAR), eq(WebhookLog.Platform.CLICKUP),
                anyString(), eq("Done"), eq("complete"),
                eq(SyncEvent.SyncStatus.SUCCESS), isNull(), anyString());
        verify(idempotencyService).markProcessed(anyString(), eq(WebhookLog.Platform.LINEAR));
    }

    @Test
    void handle_clickUpApiFails_releasesLockAndRethrows() {
        WebhookLog mockLog = WebhookLog.builder().id(1L).build();
        TaskMapping mapping = TaskMapping.builder()
                .id(1L).linearIssueId("issue-abc").clickupTaskId("cu1").linearTeamId("team1").build();

        when(auditService.logWebhook(anyString(), any(), anyString(), any(), anyBoolean()))
                .thenReturn(mockLog);
        when(idempotencyService.alreadyProcessed(anyString(), any())).thenReturn(false);
        when(loopPrevention.shouldSkip(anyString(), anyString(), anyString())).thenReturn(false);
        when(taskMappingService.findByLinearIssueId("issue-abc")).thenReturn(Optional.of(mapping));
        when(statusMappingService.toClickUpStatus("Done")).thenReturn(Optional.of("complete"));
        doThrow(new RuntimeException("ClickUp API down"))
                .when(clickUpApiClient).updateTaskStatus("cu1", "complete");

        assertThatThrownBy(() -> service.handle(ISSUE_UPDATE_PAYLOAD, "sig"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("ClickUp API down");

        // Lock must be released so retries can proceed
        verify(loopPrevention).releaseLock("CLICKUP", "cu1", "complete");
        verify(auditService).recordSyncEvent(
                any(), any(), any(), anyString(), any(), any(),
                eq(SyncEvent.SyncStatus.FAILED), anyString(), anyString());
    }

    @Test
    void handle_malformedPayload_doesNotThrow() {
        WebhookLog mockLog = WebhookLog.builder().id(1L).build();
        when(auditService.logWebhook(anyString(), any(), anyString(), any(), anyBoolean()))
                .thenReturn(mockLog);

        // Should not throw — malformed payloads are logged and swallowed
        assertThatNoException().isThrownBy(
                () -> service.handle("not valid json at all {{{", "sig"));
    }

    @Test
    void handle_nonIssueEvent_skips() {
        WebhookLog mockLog = WebhookLog.builder().id(1L).build();
        String payload = """
            {"action":"update","type":"Comment","webhookId":"wh2","data":{"id":"c1"}}
            """;
        when(auditService.logWebhook(anyString(), any(), anyString(), any(), anyBoolean()))
                .thenReturn(mockLog);
        when(idempotencyService.alreadyProcessed(anyString(), any())).thenReturn(false);

        service.handle(payload, "sig");

        verifyNoInteractions(taskMappingService, clickUpApiClient);
    }
}
