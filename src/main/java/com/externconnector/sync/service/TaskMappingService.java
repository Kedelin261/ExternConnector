package com.externconnector.sync.service;

import com.externconnector.sync.dto.TaskMappingRequest;
import com.externconnector.sync.dto.TaskMappingResponse;
import com.externconnector.sync.entity.TaskMapping;
import com.externconnector.sync.exception.ResourceNotFoundException;
import com.externconnector.sync.repository.TaskMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class TaskMappingService {

    private static final Logger log = LoggerFactory.getLogger(TaskMappingService.class);

    private final TaskMappingRepository repository;

    public TaskMappingService(TaskMappingRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<TaskMappingResponse> getAll() {
        return repository.findAll().stream()
                .map(TaskMappingResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TaskMappingResponse getById(Long id) {
        return repository.findById(id)
                .map(TaskMappingResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Task mapping not found: " + id));
    }

    @Transactional
    public TaskMappingResponse create(TaskMappingRequest req) {
        if (repository.existsByLinearIssueId(req.linearIssueId())) {
            throw new IllegalArgumentException(
                    "Task mapping already exists for Linear issue: " + req.linearIssueId());
        }
        if (repository.existsByClickupTaskId(req.clickupTaskId())) {
            throw new IllegalArgumentException(
                    "Task mapping already exists for ClickUp task: " + req.clickupTaskId());
        }

        TaskMapping mapping = TaskMapping.builder()
                .linearIssueId(req.linearIssueId())
                .linearTeamId(req.linearTeamId())
                .clickupTaskId(req.clickupTaskId())
                .clickupListId(req.clickupListId())
                .syncEnabled(true)
                .build();

        TaskMapping saved = repository.save(mapping);
        log.info("Created task mapping: Linear {} <-> ClickUp {}", req.linearIssueId(), req.clickupTaskId());
        return TaskMappingResponse.from(saved);
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Task mapping not found: " + id);
        }
        repository.deleteById(id);
        log.info("Deleted task mapping id={}", id);
    }

    @Transactional
    public TaskMappingResponse updateSyncEnabled(Long id, boolean enabled) {
        TaskMapping mapping = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task mapping not found: " + id));
        mapping.setSyncEnabled(enabled);
        return TaskMappingResponse.from(repository.save(mapping));
    }

    @Transactional(readOnly = true)
    public Optional<TaskMapping> findByLinearIssueId(String linearIssueId) {
        return repository.findActiveMappingByLinearIssueId(linearIssueId);
    }

    @Transactional(readOnly = true)
    public Optional<TaskMapping> findByClickupTaskId(String clickupTaskId) {
        return repository.findActiveMappingByClickupTaskId(clickupTaskId);
    }

    @Transactional
    public void updateStatuses(Long id, String linearStatus, String clickupStatus) {
        repository.findById(id).ifPresent(mapping -> {
            mapping.setLinearStatus(linearStatus);
            mapping.setClickupStatus(clickupStatus);
            repository.save(mapping);
        });
    }
}
