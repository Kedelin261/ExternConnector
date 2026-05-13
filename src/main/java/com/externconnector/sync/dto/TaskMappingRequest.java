package com.externconnector.sync.dto;

import jakarta.validation.constraints.NotBlank;

public record TaskMappingRequest(
        @NotBlank(message = "linearIssueId is required") String linearIssueId,
        @NotBlank(message = "linearTeamId is required")  String linearTeamId,
        @NotBlank(message = "clickupTaskId is required") String clickupTaskId,
        @NotBlank(message = "clickupListId is required") String clickupListId
) {}
