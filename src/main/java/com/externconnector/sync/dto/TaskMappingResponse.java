package com.externconnector.sync.dto;

import com.externconnector.sync.entity.TaskMapping;
import java.time.OffsetDateTime;

public record TaskMappingResponse(
        Long id,
        String linearIssueId,
        String linearTeamId,
        String clickupTaskId,
        String clickupListId,
        String linearStatus,
        String clickupStatus,
        boolean syncEnabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static TaskMappingResponse from(TaskMapping tm) {
        return new TaskMappingResponse(
                tm.getId(),
                tm.getLinearIssueId(),
                tm.getLinearTeamId(),
                tm.getClickupTaskId(),
                tm.getClickupListId(),
                tm.getLinearStatus(),
                tm.getClickupStatus(),
                tm.isSyncEnabled(),
                tm.getCreatedAt(),
                tm.getUpdatedAt()
        );
    }
}
