package com.externconnector.sync.controller;

import com.externconnector.sync.dto.*;
import com.externconnector.sync.entity.StatusMapping;
import com.externconnector.sync.exception.GlobalExceptionHandler;
import com.externconnector.sync.exception.ResourceNotFoundException;
import com.externconnector.sync.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SyncController.class)
@Import(GlobalExceptionHandler.class)
class SyncControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean private TaskMappingService taskMappingService;
    @MockBean private StatusMappingService statusMappingService;
    @MockBean private ManualSyncService manualSyncService;
    @MockBean private SyncLoopPreventionService loopPreventionService;

    // ─── Health ──────────────────────────────────────────────────────────────

    @Test
    void getHealth_returns200() throws Exception {
        when(loopPreventionService.activeLockCount()).thenReturn(3);

        mockMvc.perform(get("/sync/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.activeLocks").value(3));
    }

    // ─── Task Mappings ────────────────────────────────────────────────────────

    @Test
    void getMappings_returnsList() throws Exception {
        TaskMappingResponse r = new TaskMappingResponse(1L, "LINEAR-1", "team1", "cu1", "list1",
                "In Progress", "in progress", true, OffsetDateTime.now(), OffsetDateTime.now());
        when(taskMappingService.getAll()).thenReturn(List.of(r));

        mockMvc.perform(get("/sync/mappings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].linearIssueId").value("LINEAR-1"));
    }

    @Test
    void createMapping_validRequest_returns201() throws Exception {
        TaskMappingRequest req = new TaskMappingRequest("LINEAR-1", "team1", "cu1", "list1");
        TaskMappingResponse resp = new TaskMappingResponse(1L, "LINEAR-1", "team1", "cu1", "list1",
                null, null, true, OffsetDateTime.now(), OffsetDateTime.now());
        when(taskMappingService.create(any())).thenReturn(resp);

        mockMvc.perform(post("/sync/mappings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.linearIssueId").value("LINEAR-1"));
    }

    @Test
    void createMapping_blankLinearIssueId_returns400() throws Exception {
        TaskMappingRequest req = new TaskMappingRequest("", "team1", "cu1", "list1");

        mockMvc.perform(post("/sync/mappings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteMapping_existing_returns204() throws Exception {
        doNothing().when(taskMappingService).delete(1L);

        mockMvc.perform(delete("/sync/mappings/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteMapping_notFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Task mapping not found: 99"))
                .when(taskMappingService).delete(99L);

        mockMvc.perform(delete("/sync/mappings/99"))
                .andExpect(status().isNotFound());
    }

    // ─── Status Mappings ──────────────────────────────────────────────────────

    @Test
    void getStatusMappings_returnsList() throws Exception {
        StatusMappingResponse r = new StatusMappingResponse(1L, "Done", "complete",
                StatusMapping.SyncDirection.BOTH, OffsetDateTime.now(), OffsetDateTime.now());
        when(statusMappingService.getAll()).thenReturn(List.of(r));

        mockMvc.perform(get("/sync/status-mappings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].linearStatus").value("Done"));
    }

    @Test
    void createStatusMapping_validRequest_returns201() throws Exception {
        StatusMappingRequest req = new StatusMappingRequest("Done", "complete", StatusMapping.SyncDirection.BOTH);
        StatusMappingResponse resp = new StatusMappingResponse(1L, "Done", "complete",
                StatusMapping.SyncDirection.BOTH, OffsetDateTime.now(), OffsetDateTime.now());
        when(statusMappingService.create(any())).thenReturn(resp);

        mockMvc.perform(post("/sync/status-mappings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    // ─── Manual Sync ──────────────────────────────────────────────────────────

    @Test
    void manualSync_success_returns200() throws Exception {
        SyncResult result = SyncResult.ok("LINEAR-1", "cu1", "complete");
        when(manualSyncService.syncFromLinear(anyString())).thenReturn(result);

        ManualSyncRequest req = new ManualSyncRequest("LINEAR-1");
        mockMvc.perform(post("/sync/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void manualSync_skipped_returns422() throws Exception {
        SyncResult result = SyncResult.skipped("No task mapping found");
        when(manualSyncService.syncFromLinear(anyString())).thenReturn(result);

        ManualSyncRequest req = new ManualSyncRequest("LINEAR-1");
        mockMvc.perform(post("/sync/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void manualSync_missingLinearIssueId_returns400() throws Exception {
        ManualSyncRequest req = new ManualSyncRequest("");
        mockMvc.perform(post("/sync/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
}
