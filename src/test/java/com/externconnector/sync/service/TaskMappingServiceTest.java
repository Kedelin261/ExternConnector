package com.externconnector.sync.service;

import com.externconnector.sync.dto.TaskMappingRequest;
import com.externconnector.sync.dto.TaskMappingResponse;
import com.externconnector.sync.entity.TaskMapping;
import com.externconnector.sync.exception.ResourceNotFoundException;
import com.externconnector.sync.repository.TaskMappingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskMappingServiceTest {

    @Mock private TaskMappingRepository repository;
    @InjectMocks private TaskMappingService service;

    private TaskMapping buildMapping() {
        return TaskMapping.builder()
                .id(1L).linearIssueId("LINEAR-1").linearTeamId("team1")
                .clickupTaskId("cu1").clickupListId("list1").syncEnabled(true)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                .build();
    }

    @Test
    void getAll_returnsMappedList() {
        when(repository.findAll()).thenReturn(List.of(buildMapping()));
        List<TaskMappingResponse> result = service.getAll();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).linearIssueId()).isEqualTo("LINEAR-1");
    }

    @Test
    void create_uniqueMapping_savesSuccessfully() {
        TaskMappingRequest req = new TaskMappingRequest("LINEAR-1", "team1", "cu1", "list1");
        when(repository.existsByLinearIssueId("LINEAR-1")).thenReturn(false);
        when(repository.existsByClickupTaskId("cu1")).thenReturn(false);
        when(repository.save(any())).thenReturn(buildMapping());

        TaskMappingResponse result = service.create(req);

        assertThat(result.linearIssueId()).isEqualTo("LINEAR-1");
    }

    @Test
    void create_duplicateLinearIssueId_throwsIllegalArgument() {
        TaskMappingRequest req = new TaskMappingRequest("LINEAR-1", "team1", "cu1", "list1");
        when(repository.existsByLinearIssueId("LINEAR-1")).thenReturn(true);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Linear issue");
    }

    @Test
    void create_duplicateClickupTaskId_throwsIllegalArgument() {
        TaskMappingRequest req = new TaskMappingRequest("LINEAR-1", "team1", "cu1", "list1");
        when(repository.existsByLinearIssueId("LINEAR-1")).thenReturn(false);
        when(repository.existsByClickupTaskId("cu1")).thenReturn(true);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ClickUp task");
    }

    @Test
    void delete_existing_deletesSuccessfully() {
        when(repository.existsById(1L)).thenReturn(true);
        service.delete(1L);
        verify(repository).deleteById(1L);
    }

    @Test
    void delete_notFound_throwsResourceNotFoundException() {
        when(repository.existsById(99L)).thenReturn(false);
        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findByLinearIssueId_active_returnsMapping() {
        TaskMapping mapping = buildMapping();
        when(repository.findActiveMappingByLinearIssueId("LINEAR-1")).thenReturn(Optional.of(mapping));
        assertThat(service.findByLinearIssueId("LINEAR-1")).contains(mapping);
    }

    @Test
    void findByClickupTaskId_active_returnsMapping() {
        TaskMapping mapping = buildMapping();
        when(repository.findActiveMappingByClickupTaskId("cu1")).thenReturn(Optional.of(mapping));
        assertThat(service.findByClickupTaskId("cu1")).contains(mapping);
    }

    @Test
    void updateSyncEnabled_setsFlag() {
        TaskMapping mapping = buildMapping();
        when(repository.findById(1L)).thenReturn(Optional.of(mapping));
        when(repository.save(any())).thenReturn(mapping);

        service.updateSyncEnabled(1L, false);
        verify(repository).save(argThat(m -> !m.isSyncEnabled()));
    }
}
