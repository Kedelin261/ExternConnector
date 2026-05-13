package com.externconnector.sync.dto;

import com.externconnector.sync.entity.StatusMapping;
import java.time.OffsetDateTime;

public record StatusMappingResponse(
        Long id,
        String linearStatus,
        String clickupStatus,
        StatusMapping.SyncDirection direction,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static StatusMappingResponse from(StatusMapping sm) {
        return new StatusMappingResponse(
                sm.getId(),
                sm.getLinearStatus(),
                sm.getClickupStatus(),
                sm.getDirection(),
                sm.getCreatedAt(),
                sm.getUpdatedAt()
        );
    }
}
