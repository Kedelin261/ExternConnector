package com.externconnector.sync.controller;

import com.externconnector.sync.dto.*;
import com.externconnector.sync.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/sync")
@Tag(name = "Sync", description = "Sync management: health, mappings, manual triggers")
public class SyncController {

    private final TaskMappingService taskMappingService;
    private final StatusMappingService statusMappingService;
    private final ManualSyncService manualSyncService;
    private final SyncLoopPreventionService loopPreventionService;

    public SyncController(
            TaskMappingService taskMappingService,
            StatusMappingService statusMappingService,
            ManualSyncService manualSyncService,
            SyncLoopPreventionService loopPreventionService) {
        this.taskMappingService = taskMappingService;
        this.statusMappingService = statusMappingService;
        this.manualSyncService = manualSyncService;
        this.loopPreventionService = loopPreventionService;
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
}
