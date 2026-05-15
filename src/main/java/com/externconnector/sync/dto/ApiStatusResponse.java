package com.externconnector.sync.dto;

import java.time.OffsetDateTime;

/**
 * DTO returned by GET /sync/api-status
 * Represents live connectivity check results for Linear and ClickUp APIs.
 */
public record ApiStatusResponse(
        ServiceStatus linear,
        ServiceStatus clickup,
        DatabaseStatus database,
        OffsetDateTime checkedAt
) {

    public record ServiceStatus(
            String name,
            boolean reachable,
            long latencyMs,
            String error
    ) {}

    public record DatabaseStatus(
            boolean connected,
            long latencyMs,
            String error
    ) {}
}
