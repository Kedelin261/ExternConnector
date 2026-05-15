package com.externconnector.sync.dto;

import com.externconnector.sync.entity.SyncEvent;
import com.externconnector.sync.entity.WebhookLog;

import java.time.OffsetDateTime;

/**
 * DTO returned by GET /sync/events
 */
public record SyncEventResponse(
        Long id,
        Long taskMappingId,
        String linearIssueId,
        String clickupTaskId,
        WebhookLog.Platform sourcePlatform,
        WebhookLog.Platform targetPlatform,
        String eventType,
        String sourceStatus,
        String targetStatus,
        SyncEvent.SyncStatus status,
        String errorMessage,
        String idempotencyKey,
        OffsetDateTime createdAt
) {
    public static SyncEventResponse from(SyncEvent e) {
        String linearId = null;
        String clickupId = null;
        Long mappingId = null;
        if (e.getTaskMapping() != null) {
            mappingId = e.getTaskMapping().getId();
            linearId  = e.getTaskMapping().getLinearIssueId();
            clickupId = e.getTaskMapping().getClickupTaskId();
        }
        return new SyncEventResponse(
                e.getId(),
                mappingId,
                linearId,
                clickupId,
                e.getSourcePlatform(),
                e.getTargetPlatform(),
                e.getEventType(),
                e.getSourceStatus(),
                e.getTargetStatus(),
                e.getStatus(),
                e.getErrorMessage(),
                e.getIdempotencyKey(),
                e.getCreatedAt()
        );
    }
}
