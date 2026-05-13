package com.externconnector.sync.dto;

import com.externconnector.sync.entity.StatusMapping;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record StatusMappingRequest(
        @NotBlank(message = "linearStatus is required")  String linearStatus,
        @NotBlank(message = "clickupStatus is required") String clickupStatus,
        @NotNull(message = "direction is required")      StatusMapping.SyncDirection direction
) {}
