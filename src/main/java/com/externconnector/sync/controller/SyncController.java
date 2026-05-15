package com.externconnector.sync.controller;

import com.externconnector.sync.client.ClickUpApiClient;
import com.externconnector.sync.client.LinearApiClient;
import com.externconnector.sync.dto.*;
import com.externconnector.sync.entity.SyncEvent;
import com.externconnector.sync.entity.WebhookLog;
import com.externconnector.sync.repository.SyncEventRepository;
import com.externconnector.sync.repository.WebhookLogRepository;
import com.externconnector.sync.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/sync")
@Tag(name = "Sync", description = "Sync management: health, mappings, events, manual triggers")
public class SyncController {

    private static final Logger log = LoggerFactory.getLogger(SyncController.class);

    private final TaskMappingService taskMappingService;
    private final StatusMappingService statusMappingService;
    private final ManualSyncService manualSyncService;
    private final SyncLoopPreventionService loopPreventionService;
    private final SyncEventRepository syncEventRepository;
    private final WebhookLogRepository webhookLogRepository;
    private final LinearApiClient linearApiClient;
    private final ClickUpApiClient clickUpApiClient;
    private final JdbcTemplate jdbcTemplate;

    @Value("${linear.team.id}")
    private String linearTeamId;

    @Value("${clickup.list.id}")
    private String clickupListId;

    public SyncController(
            TaskMappingService taskMappingService,
            StatusMappingService statusMappingService,
            ManualSyncService manualSyncService,
            SyncLoopPreventionService loopPreventionService,
            SyncEventRepository syncEventRepository,
            WebhookLogRepository webhookLogRepository,
            LinearApiClient linearApiClient,
            ClickUpApiClient clickUpApiClient,
            JdbcTemplate jdbcTemplate) {
        this.taskMappingService = taskMappingService;
        this.statusMappingService = statusMappingService;
        this.manualSyncService = manualSyncService;
        this.loopPreventionService = loopPreventionService;
        this.syncEventRepository = syncEventRepository;
        this.webhookLogRepository = webhookLogRepository;
        this.linearApiClient = linearApiClient;
        this.clickUpApiClient = clickUpApiClient;
        this.jdbcTemplate = jdbcTemplate;
    }

    // ─── Health ──────────────────────────────────────────────────────────────

    @GetMapping("/health")
    @Operation(summary = "Health check")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "linear-clickup-sync",
                "activeLocks", loopPreventionService.activeLockCount()
        ));
    }

    // ─── Sync Events ──────────────────────────────────────────────────────────

    @GetMapping("/events")
    @Operation(summary = "Get paginated sync event history")
    public ResponseEntity<Map<String, Object>> getEvents(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {

        size = Math.min(size, 100); // cap to 100 per page
        Pageable pageable = PageRequest.of(page, size);
        Page<SyncEvent> resultPage = syncEventRepository.findAllByOrderByCreatedAtDesc(pageable);

        List<SyncEventResponse> events = resultPage.getContent().stream()
                .map(SyncEventResponse::from)
                .toList();

        return ResponseEntity.ok(Map.of(
                "events", events,
                "totalElements", resultPage.getTotalElements(),
                "totalPages", resultPage.getTotalPages(),
                "page", resultPage.getNumber(),
                "size", resultPage.getSize()
        ));
    }

    // ─── Webhook Logs ─────────────────────────────────────────────────────────

    @GetMapping("/webhook-logs")
    @Operation(summary = "Get recent webhook logs")
    public ResponseEntity<Map<String, Object>> getWebhookLogs(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {

        size = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, size);
        Page<WebhookLog> resultPage = webhookLogRepository.findAllByOrderByReceivedAtDesc(pageable);

        List<WebhookLogResponse> logs = resultPage.getContent().stream()
                .map(WebhookLogResponse::from)
                .toList();

        return ResponseEntity.ok(Map.of(
                "logs", logs,
                "totalElements", resultPage.getTotalElements(),
                "totalPages", resultPage.getTotalPages(),
                "page", resultPage.getNumber(),
                "size", resultPage.getSize()
        ));
    }

    // ─── API Status ───────────────────────────────────────────────────────────

    @GetMapping("/api-status")
    @Operation(summary = "Live connectivity check for Linear, ClickUp, and Database")
    public ResponseEntity<ApiStatusResponse> getApiStatus() {

        // ── Linear ping ──────────────────────────────────────────────────────
        ApiStatusResponse.ServiceStatus linearStatus;
        long linearStart = System.currentTimeMillis();
        try {
            // getWorkflowStates is lightweight — uses cache or one GraphQL call
            Map<String, String> states = linearApiClient.getWorkflowStates(linearTeamId);
            long latency = System.currentTimeMillis() - linearStart;
            linearStatus = new ApiStatusResponse.ServiceStatus(
                    "Linear", true, latency,
                    states.isEmpty() ? "Connected but no workflow states found" : null);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - linearStart;
            log.warn("Linear API status check failed: {}", e.getMessage());
            linearStatus = new ApiStatusResponse.ServiceStatus(
                    "Linear", false, latency, summarise(e));
        }

        // ── ClickUp ping ─────────────────────────────────────────────────────
        ApiStatusResponse.ServiceStatus clickupStatus;
        long clickupStart = System.currentTimeMillis();
        try {
            List<String> statuses = clickUpApiClient.getListStatuses(clickupListId);
            long latency = System.currentTimeMillis() - clickupStart;
            clickupStatus = new ApiStatusResponse.ServiceStatus(
                    "ClickUp", true, latency,
                    statuses.isEmpty() ? "Connected but no statuses found" : null);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - clickupStart;
            log.warn("ClickUp API status check failed: {}", e.getMessage());
            clickupStatus = new ApiStatusResponse.ServiceStatus(
                    "ClickUp", false, latency, summarise(e));
        }

        // ── DB ping ───────────────────────────────────────────────────────────
        ApiStatusResponse.DatabaseStatus dbStatus;
        long dbStart = System.currentTimeMillis();
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            long latency = System.currentTimeMillis() - dbStart;
            dbStatus = new ApiStatusResponse.DatabaseStatus(true, latency, null);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - dbStart;
            log.warn("Database status check failed: {}", e.getMessage());
            dbStatus = new ApiStatusResponse.DatabaseStatus(false, latency, summarise(e));
        }

        return ResponseEntity.ok(new ApiStatusResponse(
                linearStatus, clickupStatus, dbStatus, OffsetDateTime.now()));
    }

    // ─── Task Mappings ────────────────────────────────────────────────────────

    @GetMapping("/mappings")
    @Operation(summary = "List all task mappings")
    public ResponseEntity<List<TaskMappingResponse>> getMappings() {
        return ResponseEntity.ok(taskMappingService.getAll());
    }

    @PostMapping("/mappings")
    @Operation(summary = "Create a task mapping")
    public ResponseEntity<TaskMappingResponse> createMapping(
            @Valid @RequestBody TaskMappingRequest request) {
        TaskMappingResponse response = taskMappingService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/mappings/{id}")
    @Operation(summary = "Delete a task mapping")
    public ResponseEntity<Void> deleteMapping(@PathVariable Long id) {
        taskMappingService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/mappings/{id}/sync-enabled")
    @Operation(summary = "Enable/disable sync for a task mapping")
    public ResponseEntity<TaskMappingResponse> setSyncEnabled(
            @PathVariable Long id,
            @RequestParam boolean enabled) {
        return ResponseEntity.ok(taskMappingService.updateSyncEnabled(id, enabled));
    }

    // ─── Status Mappings ──────────────────────────────────────────────────────

    @GetMapping("/status-mappings")
    @Operation(summary = "List all status mappings")
    public ResponseEntity<List<StatusMappingResponse>> getStatusMappings() {
        return ResponseEntity.ok(statusMappingService.getAll());
    }

    @PostMapping("/status-mappings")
    @Operation(summary = "Create a status mapping")
    public ResponseEntity<StatusMappingResponse> createStatusMapping(
            @Valid @RequestBody StatusMappingRequest request) {
        StatusMappingResponse response = statusMappingService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/status-mappings/{id}")
    @Operation(summary = "Delete a status mapping")
    public ResponseEntity<Void> deleteStatusMapping(@PathVariable Long id) {
        statusMappingService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Manual Sync ──────────────────────────────────────────────────────────

    @PostMapping("/manual")
    @Operation(summary = "Trigger manual sync from Linear → ClickUp")
    public ResponseEntity<SyncResult> manualSync(
            @Valid @RequestBody ManualSyncRequest request) {
        SyncResult result = manualSyncService.syncFromLinear(request.linearIssueId());
        if (result.success()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(result);
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String summarise(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return e.getClass().getSimpleName();
        return msg.length() > 200 ? msg.substring(0, 200) + "…" : msg;
    }
}
