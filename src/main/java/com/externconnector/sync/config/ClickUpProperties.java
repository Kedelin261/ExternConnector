package com.externconnector.sync.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "clickup")
public record ClickUpProperties(
        @NotBlank Api api,
        @NotBlank String webhookSecret,
        @NotBlank String workspaceId,
        @NotBlank String listId
) {
    public record Api(
            @NotBlank String key,
            @NotBlank String url
    ) {}
}
