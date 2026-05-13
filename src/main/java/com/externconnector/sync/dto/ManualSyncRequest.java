package com.externconnector.sync.dto;

import jakarta.validation.constraints.NotBlank;

public record ManualSyncRequest(
        @NotBlank(message = "linearIssueId is required") String linearIssueId
) {}
