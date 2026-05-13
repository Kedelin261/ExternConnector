package com.externconnector.sync.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * ClickUp webhook payload structure.
 * Ref: https://clickup.com/api/developer-portal/webhooks
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClickUpWebhookPayload(
        @JsonProperty("webhook_id")  String webhookId,
        @JsonProperty("event")       String event,
        @JsonProperty("task_id")     String taskId,
        @JsonProperty("history_items") List<HistoryItem> historyItems
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HistoryItem(
            @JsonProperty("id")         String id,
            @JsonProperty("type")       Integer type,
            @JsonProperty("field")      String field,
            @JsonProperty("parent_id")  String parentId,
            @JsonProperty("data")       Map<String, Object> data,
            @JsonProperty("before")     StatusValue before,
            @JsonProperty("after")      StatusValue after
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StatusValue(
            @JsonProperty("status")      String status,
            @JsonProperty("color")       String color,
            @JsonProperty("type")        String type,
            @JsonProperty("orderindex")  Double orderindex
    ) {}

    public boolean isTaskStatusUpdated() {
        return "taskStatusUpdated".equals(event);
    }

    public boolean isTaskCreated() {
        return "taskCreated".equals(event);
    }

    public boolean isTaskUpdated() {
        return "taskUpdated".equals(event);
    }

    /**
     * Extract the new status from the first history item where field=status.
     */
    public String newStatus() {
        if (historyItems == null) return null;
        return historyItems.stream()
                .filter(h -> "status".equals(h.field()) && h.after() != null)
                .map(h -> h.after().status())
                .findFirst()
                .orElse(null);
    }

    /**
     * Extract the old status from the first history item where field=status.
     */
    public String oldStatus() {
        if (historyItems == null) return null;
        return historyItems.stream()
                .filter(h -> "status".equals(h.field()) && h.before() != null)
                .map(h -> h.before().status())
                .findFirst()
                .orElse(null);
    }
}
