package com.externconnector.sync.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "task_mappings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class TaskMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "linear_issue_id", nullable = false, unique = true)
    private String linearIssueId;

    @Column(name = "linear_team_id", nullable = false)
    private String linearTeamId;

    @Column(name = "clickup_task_id", nullable = false, unique = true)
    private String clickupTaskId;

    @Column(name = "clickup_list_id", nullable = false)
    private String clickupListId;

    @Column(name = "linear_status")
    private String linearStatus;

    @Column(name = "clickup_status")
    private String clickupStatus;

    @Column(name = "sync_enabled", nullable = false)
    @Builder.Default
    private boolean syncEnabled = true;

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
}
