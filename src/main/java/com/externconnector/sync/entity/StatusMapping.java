package com.externconnector.sync.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "status_mappings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class StatusMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "linear_status", nullable = false)
    private String linearStatus;

    @Column(name = "clickup_status", nullable = false)
    private String clickupStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 30)
    @Builder.Default
    private SyncDirection direction = SyncDirection.BOTH;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public enum SyncDirection {
        BOTH, LINEAR_TO_CLICKUP, CLICKUP_TO_LINEAR
    }
}
