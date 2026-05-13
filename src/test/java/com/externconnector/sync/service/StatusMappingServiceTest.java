package com.externconnector.sync.service;

import com.externconnector.sync.dto.StatusMappingRequest;
import com.externconnector.sync.dto.StatusMappingResponse;
import com.externconnector.sync.entity.StatusMapping;
import com.externconnector.sync.exception.ResourceNotFoundException;
import com.externconnector.sync.repository.StatusMappingRepository;
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
class StatusMappingServiceTest {

    @Mock private StatusMappingRepository repository;
    @InjectMocks private StatusMappingService service;

    @Test
    void getAll_returnsAllMappings() {
        StatusMapping sm = StatusMapping.builder()
                .id(1L).linearStatus("Done").clickupStatus("complete")
                .direction(StatusMapping.SyncDirection.BOTH)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                .build();
        when(repository.findAllByOrderByLinearStatusAsc()).thenReturn(List.of(sm));

        List<StatusMappingResponse> result = service.getAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).linearStatus()).isEqualTo("Done");
    }

    @Test
    void create_newMapping_savesAndReturns() {
        StatusMappingRequest req = new StatusMappingRequest("Done", "complete", StatusMapping.SyncDirection.BOTH);
        StatusMapping saved = StatusMapping.builder()
                .id(1L).linearStatus("Done").clickupStatus("complete")
                .direction(StatusMapping.SyncDirection.BOTH)
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now())
                .build();

        when(repository.existsByLinearStatusAndClickupStatus("Done", "complete")).thenReturn(false);
        when(repository.save(any())).thenReturn(saved);

        StatusMappingResponse result = service.create(req);

        assertThat(result.linearStatus()).isEqualTo("Done");
        assertThat(result.clickupStatus()).isEqualTo("complete");
    }

    @Test
    void create_duplicateMapping_throwsIllegalArgument() {
        StatusMappingRequest req = new StatusMappingRequest("Done", "complete", StatusMapping.SyncDirection.BOTH);
        when(repository.existsByLinearStatusAndClickupStatus("Done", "complete")).thenReturn(true);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void delete_existing_deletesSuccessfully() {
        when(repository.existsById(1L)).thenReturn(true);
        service.delete(1L);
        verify(repository).deleteById(1L);
    }

    @Test
    void delete_notFound_throwsResourceNotFound() {
        when(repository.existsById(99L)).thenReturn(false);
        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void toClickUpStatus_foundMapping_returnsClickUpStatus() {
        StatusMapping sm = StatusMapping.builder()
                .linearStatus("Done").clickupStatus("complete")
                .direction(StatusMapping.SyncDirection.BOTH).build();
        when(repository.findByLinearStatus("Done")).thenReturn(Optional.of(sm));

        Optional<String> result = service.toClickUpStatus("Done");

        assertThat(result).contains("complete");
    }

    @Test
    void toClickUpStatus_noMapping_returnsEmpty() {
        when(repository.findByLinearStatus("Unknown")).thenReturn(Optional.empty());
        assertThat(service.toClickUpStatus("Unknown")).isEmpty();
    }

    @Test
    void toLinearStatus_foundMapping_returnsLinearStatus() {
        StatusMapping sm = StatusMapping.builder()
                .linearStatus("In Progress").clickupStatus("in progress")
                .direction(StatusMapping.SyncDirection.BOTH).build();
        when(repository.findByClickupStatus("in progress")).thenReturn(Optional.of(sm));

        Optional<String> result = service.toLinearStatus("in progress");

        assertThat(result).contains("In Progress");
    }

    @Test
    void toLinearStatus_noMapping_returnsEmpty() {
        when(repository.findByClickupStatus("unknown status")).thenReturn(Optional.empty());
        assertThat(service.toLinearStatus("unknown status")).isEmpty();
    }
}
