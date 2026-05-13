package com.externconnector.sync.service;

import com.externconnector.sync.dto.StatusMappingRequest;
import com.externconnector.sync.dto.StatusMappingResponse;
import com.externconnector.sync.entity.StatusMapping;
import com.externconnector.sync.exception.ResourceNotFoundException;
import com.externconnector.sync.repository.StatusMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class StatusMappingService {

    private static final Logger log = LoggerFactory.getLogger(StatusMappingService.class);

    private final StatusMappingRepository repository;

    public StatusMappingService(StatusMappingRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<StatusMappingResponse> getAll() {
        return repository.findAllByOrderByLinearStatusAsc()
                .stream()
                .map(StatusMappingResponse::from)
                .toList();
    }

    @Transactional
    public StatusMappingResponse create(StatusMappingRequest req) {
        if (repository.existsByLinearStatusAndClickupStatus(req.linearStatus(), req.clickupStatus())) {
            throw new IllegalArgumentException(
                    "Status mapping already exists: " + req.linearStatus() + " <-> " + req.clickupStatus());
        }
        StatusMapping mapping = StatusMapping.builder()
                .linearStatus(req.linearStatus())
                .clickupStatus(req.clickupStatus())
                .direction(req.direction())
                .build();
        StatusMapping saved = repository.save(mapping);
        log.info("Created status mapping: {} <-> {} [{}]", req.linearStatus(), req.clickupStatus(), req.direction());
        return StatusMappingResponse.from(saved);
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Status mapping not found: " + id);
        }
        repository.deleteById(id);
        log.info("Deleted status mapping id={}", id);
    }

    /**
     * Translate a Linear status to ClickUp status.
     * Returns empty if no mapping found.
     */
    @Transactional(readOnly = true)
    public Optional<String> toClickUpStatus(String linearStatus) {
        return repository.findByLinearStatus(linearStatus)
                .map(StatusMapping::getClickupStatus);
    }

    /**
     * Translate a ClickUp status to Linear status.
     * Returns empty if no mapping found.
     */
    @Transactional(readOnly = true)
    public Optional<String> toLinearStatus(String clickupStatus) {
        return repository.findByClickupStatus(clickupStatus)
                .map(StatusMapping::getLinearStatus);
    }
}
