package com.externconnector.sync.service;

import com.externconnector.sync.client.LinearApiClient;
import com.externconnector.sync.config.ClickUpProperties;
import com.externconnector.sync.entity.SyncEvent;
import com.externconnector.sync.entity.TaskMapping;
import com.externconnector.sync.entity.WebhookLog;
import com.externconnector.sync.exception.WebhookAuthException;
import com.externconnector.sync.security.WebhookSignatureVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class ClickUpWebhookServiceTest {

    @Mock private WebhookSignatureVerifier signatureVerifier;
    @Mock private ClickUpProperties clickUpProperties;
    @Mock private ClickUpProperties.Api clickUpApi;
    @Mock private TaskMappingService taskMappingService;
    @Mock private StatusMappingService statusMappingService;
    @Mock private SyncLoopPreventionService loopPrevention;
    @Mock private IdempotencyService idempotencyService;
    @Mock private WebhookAuditService auditService;
    @Mock private LinearApiClient linearApiClient;

    private ClickUpWebhookService service;

    private static final String TASK_STATUS_PAYLOAD = """
        {
          "webhook_id": "wh1",
          "event": "taskStatusUpdated",
          "task_id": "task-xyz",
          "history_items": [
            {
              "field": "status",
              "before": {"status": "in progress"},
              "after": {"status": "complete"}
            }
          ]
        }
        """;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        when(clickUpProperties.webhookSecret()).thenReturn("cu-secret");
        // lenient — not all tests use api() sub-properties
        when(clickUpProperties.api()).thenReturn(clickUpApi);

        service = new ClickUpWebhookService(
                signatureVerifier, clickUpProperties,
                taskMappingService, statusMappingService,
                loopPrevention, idempotencyService, auditService,
                linearApiClient, objectMapper);
    }

    @Test
    void handle_invalidSignature_throwsWebhookAuthException() {
        doThrow(new WebhookAuthException("Invalid ClickUp webhook signature"))
                .when(signatureVerifier).verifyClickUp(anyString(), anyString(), anyString());

        assertThatThrownBy(() -> service.handle(TASK_STATUS_PAYLOAD, "badsig"))
                .isInstanceOf(WebhookAuthException.class);

        verifyNoInteractions(taskMappingService, linearApiClient);
    }

    @Test
    void handle_duplicateEvent_skipsProcessing() {
        WebhookLog mockLog = WebhookLog.builder().id(1L).build();
        when(auditService.logWebhook(anyString(), any(), anyString(), any(), anyBoolean()))
                .thenReturn(mockLog);
        when(idempotencyService.alreadyProcessed(anyString(), any())).thenReturn(true);

        service.handle(TASK_STATUS_PAYLOAD, "sig");

        verifyNoInteractions(taskMappingService, linearApiClient);
    }

    @Test
    void handle_loopPrevented_skipsLinearUpdate() {
        WebhookLog mockLog = WebhookLog.builder().id(1L).build();
        when(auditService.logWebhook(anyString(), any(), anyString(), any(), anyBoolean()))
                .thenReturn(mockLog);
        when(idempotencyService.alreadyProcessed(anyString(), any())).thenReturn(false);
        when(loopPrevention.shouldSkip("CLICKUP", "task-xyz", "complete")).thenReturn(true);

        service.handle(TASK_STATUS_PAYLOAD, "sig");

        verifyNoInteractions(linearApiClient);
        verify(auditService).recordSyncEvent(
                isNull(), eq(WebhookLog.Platform.CLICKUP), eq(WebhookLog.Platform.LINEAR),
                anyString(), eq("complete"), isNull(),
                eq(SyncEvent.SyncStatus.LOOP_PREVENTED), isNull(), anyString());
    }

    @Test
    void handle_successfulSync_updatesLinear() {
        WebhookLog mockLog = WebhookLog.builder().id(1L).build();
        TaskMapping mapping = TaskMapping.builder()
                .id(1L).clickupTaskId("task-xyz").linearIssueId("issue-1").linearTeamId("team1").build();

        when(auditService.logWebhook(anyString(), any(), anyString(), any(), anyBoolean()))
                .thenReturn(mockLog);
        when(idempotencyService.alreadyProcessed(anyString(), any())).thenReturn(false);
        when(loopPrevention.shouldSkip(anyString(), anyString(), anyString())).thenReturn(false);
        when(taskMappingService.findByClickupTaskId("task-xyz")).thenReturn(Optional.of(mapping));
        when(statusMappingService.toLinearStatus("complete")).thenReturn(Optional.of("Done"));
        doNothing().when(linearApiClient).updateIssueStatus("issue-1", "Done", "team1");

        service.handle(TASK_STATUS_PAYLOAD, "sig");

        verify(loopPrevention).registerSyncOrigin("LINEAR", "issue-1", "Done");
        verify(linearApiClient).updateIssueStatus("issue-1", "Done", "team1");
        verify(taskMappingService).updateStatuses(1L, "Done", "complete");
        verify(auditService).recordSyncEvent(
                eq(mapping), eq(WebhookLog.Platform.CLICKUP), eq(WebhookLog.Platform.LINEAR),
                anyString(), eq("complete"), eq("Done"),
                eq(SyncEvent.SyncStatus.SUCCESS), isNull(), anyString());
    }

    @Test
    void handle_linearApiFails_releasesLockAndRethrows() {
        WebhookLog mockLog = WebhookLog.builder().id(1L).build();
        TaskMapping mapping = TaskMapping.builder()
                .id(1L).clickupTaskId("task-xyz").linearIssueId("issue-1").linearTeamId("team1").build();

        when(auditService.logWebhook(anyString(), any(), anyString(), any(), anyBoolean()))
                .thenReturn(mockLog);
        when(idempotencyService.alreadyProcessed(anyString(), any())).thenReturn(false);
        when(loopPrevention.shouldSkip(anyString(), anyString(), anyString())).thenReturn(false);
        when(taskMappingService.findByClickupTaskId("task-xyz")).thenReturn(Optional.of(mapping));
        when(statusMappingService.toLinearStatus("complete")).thenReturn(Optional.of("Done"));
        doThrow(new RuntimeException("Linear API timeout"))
                .when(linearApiClient).updateIssueStatus("issue-1", "Done", "team1");

        assertThatThrownBy(() -> service.handle(TASK_STATUS_PAYLOAD, "sig"))
                .isInstanceOf(RuntimeException.class);

        verify(loopPrevention).releaseLock("LINEAR", "issue-1", "Done");
        verify(auditService).recordSyncEvent(
                any(), any(), any(), anyString(), any(), any(),
                eq(SyncEvent.SyncStatus.FAILED), anyString(), anyString());
    }

    @Test
    void handle_noTaskMapping_skipsSync() {
        WebhookLog mockLog = WebhookLog.builder().id(1L).build();
        when(auditService.logWebhook(anyString(), any(), anyString(), any(), anyBoolean()))
                .thenReturn(mockLog);
        when(idempotencyService.alreadyProcessed(anyString(), any())).thenReturn(false);
        when(loopPrevention.shouldSkip(anyString(), anyString(), anyString())).thenReturn(false);
        when(taskMappingService.findByClickupTaskId("task-xyz")).thenReturn(Optional.empty());

        service.handle(TASK_STATUS_PAYLOAD, "sig");

        verifyNoInteractions(linearApiClient);
    }
}
