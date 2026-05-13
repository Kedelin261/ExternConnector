package com.externconnector.sync.service;

import com.externconnector.sync.client.ClickUpApiClient;
import com.externconnector.sync.client.LinearApiClient;
import com.externconnector.sync.dto.SyncResult;
import com.externconnector.sync.entity.SyncEvent;
import com.externconnector.sync.entity.TaskMapping;
import com.externconnector.sync.entity.WebhookLog;
import com.externconnector.sync.exception.ResourceNotFoundException;
import com.externconnector.sync.repository.TaskMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Manual sync trigger — pull Linear issue state and push to ClickUp.
 * Used for initial sync or recovery after failures.
 */
@Service
public class ManualSyncService {

    private static final Logger log = LoggerFactory.getLogger(ManualSyncService.class);

    private final TaskMappingRepository taskMappingRepository;
    private final LinearApiClient linearApiClient;
    private final ClickUpApiClient clickUpApiClient;
    private final StatusMappingService statusMappingService;
    private final WebhookAuditService auditService;

    public ManualSyncService(
            TaskMappingRepository taskMappingRepository,
            LinearApiClient linearApiClient,
            ClickUpApiClient clickUpApiClient,
            StatusMappingService statusMappingService,
            WebhookAuditService auditService) {
        this.taskMappingRepository = taskMappingRepository;
        this.linearApiClient = linearApiClient;
        this.clickUpApiClient = clickUpApiClient;
        this.statusMappingService = statusMappingService;
        this.auditService = auditService;
    }

    /**
     * Manual sync: fetch Linear issue → update ClickUp.
     */
    public SyncResult syncFromLinear(String linearIssueId) {
        log.info("Manual sync triggered for Linear issue {}", linearIssueId);

        // 1. Find mapping
        Optional<TaskMapping> mappingOpt = taskMappingRepository.findActiveMappingByLinearIssueId(linearIssueId);
        if (mappingOpt.isEmpty()) {
            return SyncResult.skipped("No active task mapping for Linear issue: " + linearIssueId);
        }
        TaskMapping mapping = mappingOpt.get();

        // 2. Fetch current Linear state
        Optional<LinearApiClient.LinearIssueDetails> issueOpt = linearApiClient.getIssue(linearIssueId);
        if (issueOpt.isEmpty()) {
            return SyncResult.failed("Could not fetch Linear issue: " + linearIssueId);
        }
        LinearApiClient.LinearIssueDetails issue = issueOpt.get();
        String linearStatus = issue.stateName();

        // 3. Translate status
        Optional<String> clickupStatusOpt = statusMappingService.toClickUpStatus(linearStatus);
        if (clickupStatusOpt.isEmpty()) {
            return SyncResult.skipped("No ClickUp status mapping for Linear status: " + linearStatus);
        }
        String clickupStatus = clickupStatusOpt.get();

        // 4. Update ClickUp
        try {
            clickUpApiClient.updateTaskStatus(mapping.getClickupTaskId(), clickupStatus);
            mapping.setLinearStatus(linearStatus);
            mapping.setClickupStatus(clickupStatus);
            taskMappingRepository.save(mapping);

            auditService.recordSyncEvent(mapping, WebhookLog.Platform.LINEAR,
                    WebhookLog.Platform.CLICKUP, "manual_sync", linearStatus, clickupStatus,
                    SyncEvent.SyncStatus.SUCCESS, null, "manual:" + linearIssueId);

            log.info("Manual sync success: Linear {} ({}) -> ClickUp {} ({})",
                    linearIssueId, linearStatus, mapping.getClickupTaskId(), clickupStatus);

            return SyncResult.ok(linearIssueId, mapping.getClickupTaskId(), clickupStatus);

        } catch (Exception e) {
            auditService.recordSyncEvent(mapping, WebhookLog.Platform.LINEAR,
                    WebhookLog.Platform.CLICKUP, "manual_sync", linearStatus, clickupStatus,
                    SyncEvent.SyncStatus.FAILED, e.getMessage(), "manual:" + linearIssueId);

            log.error("Manual sync failed for Linear {}: {}", linearIssueId, e.getMessage());
            return SyncResult.failed("Sync failed: " + e.getMessage());
        }
    }
}
