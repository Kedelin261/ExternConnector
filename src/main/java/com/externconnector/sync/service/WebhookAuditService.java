package com.externconnector.sync.service;

import com.externconnector.sync.entity.SyncEvent;
import com.externconnector.sync.entity.TaskMapping;
import com.externconnector.sync.entity.WebhookLog;
import com.externconnector.sync.repository.SyncEventRepository;
import com.externconnector.sync.repository.WebhookLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Immutable audit trail for all webhook events and sync actions.
 * Uses REQUIRES_NEW propagation so audit writes never roll back with business logic.
 */
@Service
public class WebhookAuditService {

    private static final Logger log = LoggerFactory.getLogger(WebhookAuditService.class);

    private final WebhookLogRepository webhookLogRepository;
    private final SyncEventRepository syncEventRepository;
    private final ObjectMapper objectMapper;

    public WebhookAuditService(
            WebhookLogRepository webhookLogRepository,
            SyncEventRepository syncEventRepository,
            ObjectMapper objectMapper) {
        this.webhookLogRepository = webhookLogRepository;
        this.syncEventRepository = syncEventRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Log a received webhook. Always persists regardless of processing outcome.
     * If event_id + source already exists (duplicate delivery), returns the existing row.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WebhookLog logWebhook(String eventId, WebhookLog.Platform source,
                                  String eventType, Object payload, boolean signatureValid) {
        // Guard against duplicate-key on idempotent retries
        return webhookLogRepository.findByEventIdAndSource(eventId, source)
                .orElseGet(() -> {
                    String payloadJson;
                    try {
                        payloadJson = payload instanceof String s ? s : objectMapper.writeValueAsString(payload);
                    } catch (Exception e) {
                        payloadJson = "{}";
                    }

                    WebhookLog wl = WebhookLog.builder()
                            .eventId(eventId)
                            .source(source)
                            .eventType(eventType)
                            .payload(payloadJson)
                            .signatureValid(signatureValid)
                            .processed(false)
                            .build();

                    return webhookLogRepository.save(wl);
                });
    }

    /**
     * Mark a webhook log as processed (or failed).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markWebhookProcessed(Long webhookLogId, boolean success, String error) {
        webhookLogRepository.findById(webhookLogId).ifPresent(wl -> {
            wl.setProcessed(success);
            wl.setProcessingError(error);
            wl.setProcessedAt(OffsetDateTime.now());
            webhookLogRepository.save(wl);
        });
    }

    /**
     * Record a sync event outcome.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SyncEvent recordSyncEvent(
            TaskMapping taskMapping,
            WebhookLog.Platform sourcePlatform,
            WebhookLog.Platform targetPlatform,
            String eventType,
            String sourceStatus,
            String targetStatus,
            SyncEvent.SyncStatus status,
            String errorMessage,
            String idempotencyKey) {

        SyncEvent event = SyncEvent.builder()
                .taskMapping(taskMapping)
                .sourcePlatform(sourcePlatform)
                .targetPlatform(targetPlatform)
                .eventType(eventType)
                .sourceStatus(sourceStatus)
                .targetStatus(targetStatus)
                .status(status)
                .errorMessage(errorMessage)
                .idempotencyKey(idempotencyKey)
                .build();

        SyncEvent saved = syncEventRepository.save(event);
        log.debug("Recorded sync event: {} -> {} [{}] status={}", sourcePlatform, targetPlatform, eventType, status);
        return saved;
    }

    /**
     * Cleanup old logs to prevent unbounded table growth.
     * Retains 90 days of history. Runs daily at 2am.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupOldLogs() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(90);
        int deletedWebhooks = webhookLogRepository.deleteOlderThan(cutoff);
        int deletedSyncEvents = syncEventRepository.deleteOlderThan(cutoff);
        if (deletedWebhooks + deletedSyncEvents > 0) {
            log.info("Cleanup: deleted {} webhook logs and {} sync events older than 90 days",
                    deletedWebhooks, deletedSyncEvents);
        }
    }
}
