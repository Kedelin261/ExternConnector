package com.externconnector.sync.service;

import com.externconnector.sync.client.LinearApiClient;
import com.externconnector.sync.config.ClickUpProperties;
import com.externconnector.sync.dto.ClickUpWebhookPayload;
import com.externconnector.sync.entity.SyncEvent;
import com.externconnector.sync.entity.TaskMapping;
import com.externconnector.sync.entity.WebhookLog;
import com.externconnector.sync.security.WebhookSignatureVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Processes incoming ClickUp webhooks.
 * Flow:
 *   1. Verify HMAC signature
 *   2. Check idempotency
 *   3. Log webhook to DB
 *   4. Check loop prevention
 *   5. Translate status via StatusMappingService
 *   6. Find task mapping
 *   7. Update Linear issue
 *   8. Record sync event
 */
@Service
public class ClickUpWebhookService {

    private static final Logger log = LoggerFactory.getLogger(ClickUpWebhookService.class);

    private final WebhookSignatureVerifier signatureVerifier;
    private final ClickUpProperties clickUpProperties;
    private final TaskMappingService taskMappingService;
    private final StatusMappingService statusMappingService;
    private final SyncLoopPreventionService loopPrevention;
    private final IdempotencyService idempotencyService;
    private final WebhookAuditService auditService;
    private final LinearApiClient linearApiClient;
    private final ObjectMapper objectMapper;

    public ClickUpWebhookService(
            WebhookSignatureVerifier signatureVerifier,
            ClickUpProperties clickUpProperties,
            TaskMappingService taskMappingService,
            StatusMappingService statusMappingService,
            SyncLoopPreventionService loopPrevention,
            IdempotencyService idempotencyService,
            WebhookAuditService auditService,
            LinearApiClient linearApiClient,
            ObjectMapper objectMapper) {
        this.signatureVerifier = signatureVerifier;
        this.clickUpProperties = clickUpProperties;
        this.taskMappingService = taskMappingService;
        this.statusMappingService = statusMappingService;
        this.loopPrevention = loopPrevention;
        this.idempotencyService = idempotencyService;
        this.auditService = auditService;
        this.linearApiClient = linearApiClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Handle a raw ClickUp webhook.
     *
     * @param rawBody   raw request body (needed for HMAC)
     * @param signature X-Signature header value
     */
    public void handle(String rawBody, String signature) {
        // 1. Verify HMAC signature
        signatureVerifier.verifyClickUp(rawBody, signature, clickUpProperties.webhookSecret());

        // 2. Parse payload
        ClickUpWebhookPayload payload;
        try {
            payload = objectMapper.readValue(rawBody, ClickUpWebhookPayload.class);
        } catch (Exception e) {
            log.warn("Failed to parse ClickUp webhook payload: {}", e.getMessage());
            auditService.logWebhook(
                    UUID.randomUUID().toString(), WebhookLog.Platform.CLICKUP,
                    "PARSE_ERROR", rawBody, true);
            return;
        }

        String eventId = buildClickUpEventId(payload);
        String eventType = payload.event() != null ? payload.event() : "unknown";

        // 3. Log webhook
        Long webhookLogId = auditService.logWebhook(
                eventId, WebhookLog.Platform.CLICKUP, eventType, rawBody, true).getId();

        // 4. Idempotency check
        if (idempotencyService.alreadyProcessed(eventId, WebhookLog.Platform.CLICKUP)) {
            log.info("Duplicate ClickUp webhook event {}, skipping", eventId);
            auditService.markWebhookProcessed(webhookLogId, true, "DUPLICATE");
            return;
        }

        try {
            processClickUpWebhook(payload, eventId, eventType, webhookLogId);
            idempotencyService.markProcessed(eventId, WebhookLog.Platform.CLICKUP);
            auditService.markWebhookProcessed(webhookLogId, true, null);
        } catch (Exception e) {
            log.error("Error processing ClickUp webhook {}: {}", eventId, e.getMessage(), e);
            auditService.markWebhookProcessed(webhookLogId, false, e.getMessage());
            throw e;
        }
    }

    private void processClickUpWebhook(ClickUpWebhookPayload payload, String eventId,
                                        String eventType, Long webhookLogId) {
        // Only process status update events
        if (!payload.isTaskStatusUpdated() && !payload.isTaskUpdated()) {
            log.debug("Ignoring non-status ClickUp event: {}", eventType);
            return;
        }

        String clickupTaskId = payload.taskId();
        String newStatus = payload.newStatus();

        if (clickupTaskId == null) {
            log.warn("ClickUp webhook missing taskId: {}", eventId);
            return;
        }

        // For taskUpdated events, only process if there's a status change
        if (payload.isTaskUpdated() && newStatus == null) {
            log.debug("ClickUp taskUpdated with no status change, skipping: {}", eventId);
            return;
        }

        if (newStatus == null) {
            log.warn("ClickUp webhook missing new status for task {}: {}", clickupTaskId, eventId);
            return;
        }

        // 5. Loop prevention
        if (loopPrevention.shouldSkip("CLICKUP", clickupTaskId, newStatus)) {
            auditService.recordSyncEvent(null, WebhookLog.Platform.CLICKUP,
                    WebhookLog.Platform.LINEAR, eventType, newStatus, null,
                    SyncEvent.SyncStatus.LOOP_PREVENTED, null, eventId);
            return;
        }

        // 6. Find task mapping
        Optional<TaskMapping> mappingOpt = taskMappingService.findByClickupTaskId(clickupTaskId);
        if (mappingOpt.isEmpty()) {
            log.info("No active task mapping for ClickUp task {}, skipping sync", clickupTaskId);
            return;
        }
        TaskMapping mapping = mappingOpt.get();

        // 7. Translate status
        Optional<String> linearStatusOpt = statusMappingService.toLinearStatus(newStatus);
        if (linearStatusOpt.isEmpty()) {
            // Try case-insensitive match
            linearStatusOpt = statusMappingService.toLinearStatus(newStatus.toLowerCase());
        }
        if (linearStatusOpt.isEmpty()) {
            log.warn("No Linear status mapping for ClickUp status '{}', skipping", newStatus);
            auditService.recordSyncEvent(mapping, WebhookLog.Platform.CLICKUP,
                    WebhookLog.Platform.LINEAR, eventType, newStatus, null,
                    SyncEvent.SyncStatus.SKIPPED, "No status mapping found", eventId);
            return;
        }
        String linearStatus = linearStatusOpt.get();

        // 8. Register loop prevention BEFORE updating Linear
        loopPrevention.registerSyncOrigin("LINEAR", mapping.getLinearIssueId(), linearStatus);

        try {
            // 9. Update Linear
            linearApiClient.updateIssueStatus(
                    mapping.getLinearIssueId(), linearStatus, mapping.getLinearTeamId());

            // 10. Update mapping status record
            taskMappingService.updateStatuses(mapping.getId(), linearStatus, newStatus);

            // 11. Record success
            auditService.recordSyncEvent(mapping, WebhookLog.Platform.CLICKUP,
                    WebhookLog.Platform.LINEAR, eventType, newStatus, linearStatus,
                    SyncEvent.SyncStatus.SUCCESS, null, eventId);

            log.info("Synced ClickUp task {} (status: {}) -> Linear issue {} (status: {})",
                    clickupTaskId, newStatus, mapping.getLinearIssueId(), linearStatus);

        } catch (Exception e) {
            loopPrevention.releaseLock("LINEAR", mapping.getLinearIssueId(), linearStatus);

            auditService.recordSyncEvent(mapping, WebhookLog.Platform.CLICKUP,
                    WebhookLog.Platform.LINEAR, eventType, newStatus, linearStatus,
                    SyncEvent.SyncStatus.FAILED, e.getMessage(), eventId);

            log.error("Failed to sync ClickUp {} -> Linear {}: {}",
                    clickupTaskId, mapping.getLinearIssueId(), e.getMessage());
            throw e;
        }
    }

    private String buildClickUpEventId(ClickUpWebhookPayload payload) {
        if (payload.webhookId() != null && payload.taskId() != null && payload.event() != null) {
            String statusPart = payload.newStatus() != null ? ":" + payload.newStatus() : "";
            return "clickup:" + payload.webhookId() + ":" + payload.taskId() + statusPart;
        }
        if (payload.taskId() != null && payload.event() != null) {
            return "clickup:" + payload.taskId() + ":" + payload.event();
        }
        return "clickup:" + UUID.randomUUID();
    }
}
