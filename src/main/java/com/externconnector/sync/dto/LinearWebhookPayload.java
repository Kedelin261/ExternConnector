package com.externconnector.sync.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Linear webhook payload structure.
 * Ref: https://developers.linear.app/docs/sdk/webhooks
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LinearWebhookPayload(
        @JsonProperty("action")   String action,
        @JsonProperty("type")     String type,
        @JsonProperty("data")     IssueData data,
        @JsonProperty("url")      String url,
        @JsonProperty("webhookTimestamp") Long webhookTimestamp,
        @JsonProperty("webhookId")        String webhookId
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IssueData(
            @JsonProperty("id")          String id,
            @JsonProperty("title")       String title,
            @JsonProperty("description") String description,
            @JsonProperty("state")       StateData state,
            @JsonProperty("team")        TeamData team,
            @JsonProperty("assignee")    AssigneeData assignee,
            @JsonProperty("priority")    Integer priority,
            @JsonProperty("identifier")  String identifier,
            @JsonProperty("url")         String url
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StateData(
            @JsonProperty("id")   String id,
            @JsonProperty("name") String name,
            @JsonProperty("type") String type
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TeamData(
            @JsonProperty("id")   String id,
            @JsonProperty("name") String name,
            @JsonProperty("key")  String key
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AssigneeData(
            @JsonProperty("id")    String id,
            @JsonProperty("name")  String name,
            @JsonProperty("email") String email
    ) {}

    public boolean isIssueEvent() {
        return "Issue".equals(type);
    }

    public boolean isUpdateAction() {
        return "update".equals(action);
    }

    public boolean isCreateAction() {
        return "create".equals(action);
    }

    public String issueId() {
        return data != null ? data.id() : null;
    }

    public String statusName() {
        return data != null && data.state() != null ? data.state().name() : null;
    }
}
