package com.externconnector.sync.dto;

public record SyncResult(
        boolean success,
        String message,
        String linearIssueId,
        String clickupTaskId,
        String syncedStatus
) {
    public static SyncResult ok(String linearIssueId, String clickupTaskId, String syncedStatus) {
        return new SyncResult(true, "Sync completed successfully", linearIssueId, clickupTaskId, syncedStatus);
    }

    public static SyncResult skipped(String message) {
        return new SyncResult(false, message, null, null, null);
    }

    public static SyncResult failed(String message) {
        return new SyncResult(false, message, null, null, null);
    }
}
