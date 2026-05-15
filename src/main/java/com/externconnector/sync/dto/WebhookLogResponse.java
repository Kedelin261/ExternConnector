package com.externconnector.sync.dto;

import com.externconnector.sync.entity.WebhookLog;

import java.time.OffsetDateTime;

/**
 * DTO returned by GET /sync/webhook-logs
 */
public record WebhookLogResponse(
        Long id,
        String eventId,
        WebhookLog.Platform source,
        String eventType,
        boolean signatureValid,
        boolean processed,
        String processingError,
        OffsetDateTime receivedAt,
        OffsetDateTime processedAt
) {
    public static WebhookLogResponse from(WebhookLog w) {
        return new WebhookLogResponse(
                w.getId(),
                w.getEventId(),
                w.getSource(),
                w.getEventType(),
                w.isSignatureValid(),
                w.isProcessed(),
                w.getProcessingError(),
                w.getReceivedAt(),
                w.getProcessedAt()
        );
    }
}
