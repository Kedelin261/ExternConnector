package com.externconnector.sync.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "sync_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class SyncEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_mapping_id")
    private TaskMapping taskMapping;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_platform", nullable = false, length = 20)
    private WebhookLog.Platform sourcePlatform;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_platform", nullable = false, length = 20)
    private WebhookLog.Platform targetPlatform;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "source_status")
    private String sourceStatus;

    @Column(name = "target_status")
    private String targetStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SyncStatus status;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public enum SyncStatus {
        SUCCESS, FAILED, SKIPPED, LOOP_PREVENTED
    }
}
