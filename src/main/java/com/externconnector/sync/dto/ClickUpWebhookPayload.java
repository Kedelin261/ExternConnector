package com.externconnector.sync.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * ClickUp webhook payload structure.
 * Ref: https://clickup.com/api/developer-portal/webhooks
 *
 * NOTE: history_items[].after and .before are POLYMORPHIC — they are:
 *   - A JSON object (StatusValue) when field="status"
 *   - A plain string (Quill delta JSON) when field="content"
 *   - Various other types for other fields (assignee, priority, etc.)
 * We use JsonNode to safely handle all cases without deserialization failure.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClickUpWebhookPayload(
        @JsonProperty("webhook_id")     String webhookId,
        @JsonProperty("event")          String event,
        @JsonProperty("task_id")        String taskId,
        @JsonProperty("history_items")  List<HistoryItem> historyItems
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HistoryItem(
            @JsonProperty("id")         String id,
            @JsonProperty("type")       Integer type,
            @JsonProperty("field")      String field,
            @JsonProperty("parent_id")  String parentId,
            @JsonProperty("before")     JsonNode before,   // polymorphic: object or string
            @JsonProperty("after")      JsonNode after     // polymorphic: object or string
    ) {
        /**
         * Extract status string from the after node if this is a status field item.
         * Returns null if not a status item or after node is absent/not an object.
         */
        public String afterStatus() {
            if (after == null || !after.isObject()) return null;
            JsonNode s = after.get("status");
            return (s != null && !s.isNull()) ? s.asText(null) : null;
        }

        /**
         * Extract status string from the before node if this is a status field item.
         */
        public String beforeStatus() {
            if (before == null || !before.isObject()) return null;
            JsonNode s = before.get("status");
            return (s != null && !s.isNull()) ? s.asText(null) : null;
        }
    }

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
     * Extract the new status from history items where field=status.
     * Works for both taskStatusUpdated and taskUpdated events.
     */
    public String newStatus() {
        if (historyItems == null) return null;
        return historyItems.stream()
                .filter(h -> "status".equals(h.field()))
                .map(HistoryItem::afterStatus)
                .filter(s -> s != null && !s.isBlank())
                .findFirst()
                .orElse(null);
    }

    /**
     * Extract the old status from history items where field=status.
     */
    public String oldStatus() {
        if (historyItems == null) return null;
        return historyItems.stream()
                .filter(h -> "status".equals(h.field()))
                .map(HistoryItem::beforeStatus)
                .filter(s -> s != null && !s.isBlank())
                .findFirst()
                .orElse(null);
    }
}
