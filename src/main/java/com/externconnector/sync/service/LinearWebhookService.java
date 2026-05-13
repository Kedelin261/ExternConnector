package com.externconnector.sync.service;

import com.externconnector.sync.client.ClickUpApiClient;
import com.externconnector.sync.config.LinearProperties;
import com.externconnector.sync.dto.LinearWebhookPayload;
import com.externconnector.sync.entity.SyncEvent;
import com.externconnector.sync.entity.TaskMapping;
import com.externconnector.sync.entity.WebhookLog;
import com.externconnector.sync.security.WebhookSignatureVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Processes incoming Linear webhooks.
 * Flow:
 *   1. Verify HMAC signature
 *   2. Check idempotency (avoid duplicate processing)
 *   3. Log webhook to DB
 *   4. Check loop prevention
 *   5. Translate status via StatusMappingService
 *   6. Find task mapping
 *   7. Update ClickUp task
 *   8. Record sync event
 */
@Service
public class LinearWebhookService {

    private static final Logger log = LoggerFactory.getLogger(LinearWebhookService.class);

    private final WebhookSignatureVerifier signatureVerifier;
    private final LinearProperties linearProperties;
    private final TaskMappingService taskMappingService;
    private final StatusMappingService statusMappingService;
    private final SyncLoopPreventionService loopPrevention;
    private final IdempotencyService idempotencyService;
    private final WebhookAuditService auditService;
    private final ClickUpApiClient clickUpApiClient;
    private final ObjectMapper objectMapper;

    public LinearWebhookService(
            WebhookSignatureVerifier signatureVerifier,
            LinearProperties linearProperties,
            TaskMappingService taskMappingService,
            StatusMappingService statusMappingService,
            SyncLoopPreventionService loopPrevention,
            IdempotencyService idempotencyService,
            WebhookAuditService auditService,
            ClickUpApiClient clickUpApiClient,
            ObjectMapper objectMapper) {
        this.signatureVerifier = signatureVerifier;
        this.linearProperties = linearProperties;
        this.taskMappingService = taskMappingService;
        this.statusMappingService = statusMappingService;
        this.loopPrevention = loopPrevention;
        this.idempotencyService = idempotencyService;
        this.auditService = auditService;
        this.clickUpApiClient = clickUpApiClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Handle a raw Linear webhook.
     *
     * @param rawBody   the raw request body bytes (needed for HMAC)
     * @param signature the X-Linear-Signature header value
     */
    public void handle(String rawBody, String signature) {
        // 1. Verify HMAC signature (throws WebhookAuthException on failure)
        signatureVerifier.verifyLinear(rawBody, signature, linearProperties.webhookSecret());

        // 2. Parse payload
        LinearWebhookPayload payload;
        try {
            payload = objectMapper.readValue(rawBody, LinearWebhookPayload.class);
        } catch (Exception e) {
            log.warn("Failed to parse Linear webhook payload: {}", e.getMessage());
            // Log but don't throw — return 200 to prevent Linear from retrying malformed payloads
            auditService.logWebhook(
                    UUID.randomUUID().toString(), WebhookLog.Platform.LINEAR,
                    "PARSE_ERROR", rawBody, true);
            return;
        }

        String eventId = buildLinearEventId(payload);
        String eventType = (payload.type() != null ? payload.type() : "unknown") +
                           "." + (payload.action() != null ? payload.action() : "unknown");

        // 3. Log webhook (audit, always persists)
        Long webhookLogId = auditService.logWebhook(
                eventId, WebhookLog.Platform.LINEAR, eventType, rawBody, true).getId();

        // 4. Idempotency check
        if (idempotencyService.alreadyProcessed(eventId, WebhookLog.Platform.LINEAR)) {
            log.info("Duplicate Linear webhook event {}, skipping", eventId);
            auditService.markWebhookProcessed(webhookLogId, true, "DUPLICATE");
            return;
        }

        try {
            processLinearWebhook(payload, eventId, eventType, webhookLogId);
            idempotencyService.markProcessed(eventId, WebhookLog.Platform.LINEAR);
            auditService.markWebhookProcessed(webhookLogId, true, null);
        } catch (Exception e) {
            log.error("Error processing Linear webhook {}: {}", eventId, e.getMessage(), e);
            auditService.markWebhookProcessed(webhookLogId, false, e.getMessage());
            // Re-throw so the controller can return 500 and Linear will retry
            throw e;
        }
    }

    private void processLinearWebhook(LinearWebhookPayload payload, String eventId,
                                       String eventType, Long webhookLogId) {
        // Only process Issue events with create/update actions
        if (!payload.isIssueEvent()) {
            log.debug("Ignoring non-Issue Linear event: {}", eventType);
            return;
        }
        if (!payload.isUpdateAction() && !payload.isCreateAction()) {
            log.debug("Ignoring non-update/create Linear action: {}", payload.action());
            return;
        }

        String linearIssueId = payload.issueId();
        String newStatus = payload.statusName();

        if (linearIssueId == null || newStatus == null) {
            log.warn("Linear webhook missing issueId or status: {}", eventId);
            return;
        }

        // 5. Loop prevention — skip if we triggered this status update ourselves
        if (loopPrevention.shouldSkip("LINEAR", linearIssueId, newStatus)) {
            auditService.recordSyncEvent(null, WebhookLog.Platform.LINEAR,
                    WebhookLog.Platform.CLICKUP, eventType, newStatus, null,
                    SyncEvent.SyncStatus.LOOP_PREVENTED, null, eventId);
            return;
        }

        // 6. Find task mapping
        Optional<TaskMapping> mappingOpt = taskMappingService.findByLinearIssueId(linearIssueId);
        if (mappingOpt.isEmpty()) {
            log.info("No active task mapping for Linear issue {}, skipping sync", linearIssueId);
            return;
        }
        TaskMapping mapping = mappingOpt.get();

        // 7. Translate status
        Optional<String> clickupStatusOpt = statusMappingService.toClickUpStatus(newStatus);
        if (clickupStatusOpt.isEmpty()) {
            log.warn("No ClickUp status mapping for Linear status '{}', skipping", newStatus);
            auditService.recordSyncEvent(mapping, WebhookLog.Platform.LINEAR,
                    WebhookLog.Platform.CLICKUP, eventType, newStatus, null,
                    SyncEvent.SyncStatus.SKIPPED, "No status mapping found", eventId);
            return;
        }
        String clickupStatus = clickupStatusOpt.get();

        // 8. Register loop prevention BEFORE updating ClickUp
        // (so when ClickUp fires its webhook back, we skip it)
        loopPrevention.registerSyncOrigin("CLICKUP", mapping.getClickupTaskId(), clickupStatus);

        try {
            // 9. Update ClickUp
            clickUpApiClient.updateTaskStatus(mapping.getClickupTaskId(), clickupStatus);

            // 10. Update mapping status record
            taskMappingService.updateStatuses(mapping.getId(), newStatus, clickupStatus);

            // 11. Record success
            auditService.recordSyncEvent(mapping, WebhookLog.Platform.LINEAR,
                    WebhookLog.Platform.CLICKUP, eventType, newStatus, clickupStatus,
                    SyncEvent.SyncStatus.SUCCESS, null, eventId);

            log.info("Synced Linear issue {} (status: {}) -> ClickUp task {} (status: {})",
                    linearIssueId, newStatus, mapping.getClickupTaskId(), clickupStatus);

        } catch (Exception e) {
            // Release the loop prevention lock so legitimate retries aren't blocked
            loopPrevention.releaseLock("CLICKUP", mapping.getClickupTaskId(), clickupStatus);

            auditService.recordSyncEvent(mapping, WebhookLog.Platform.LINEAR,
                    WebhookLog.Platform.CLICKUP, eventType, newStatus, clickupStatus,
                    SyncEvent.SyncStatus.FAILED, e.getMessage(), eventId);

            log.error("Failed to sync Linear {} -> ClickUp {}: {}",
                    linearIssueId, mapping.getClickupTaskId(), e.getMessage());
            throw e;
        }
    }

    private String buildLinearEventId(LinearWebhookPayload payload) {
        // Linear includes webhookId + issueId + action for a stable idempotency key
        if (payload.webhookId() != null && payload.issueId() != null && payload.action() != null) {
            return "linear:" + payload.webhookId() + ":" + payload.issueId() + ":" + payload.action();
        }
        if (payload.issueId() != null && payload.action() != null && payload.webhookTimestamp() != null) {
            return "linear:" + payload.issueId() + ":" + payload.action() + ":" + payload.webhookTimestamp();
        }
        return "linear:" + UUID.randomUUID();
    }
}
