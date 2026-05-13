package com.externconnector.sync.repository;

import com.externconnector.sync.entity.TaskMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TaskMappingRepository extends JpaRepository<TaskMapping, Long> {

    Optional<TaskMapping> findByLinearIssueId(String linearIssueId);

    Optional<TaskMapping> findByClickupTaskId(String clickupTaskId);

    boolean existsByLinearIssueId(String linearIssueId);

    boolean existsByClickupTaskId(String clickupTaskId);

    @Query("SELECT t FROM TaskMapping t WHERE t.linearIssueId = :linearIssueId AND t.syncEnabled = true")
    Optional<TaskMapping> findActiveMappingByLinearIssueId(String linearIssueId);

    @Query("SELECT t FROM TaskMapping t WHERE t.clickupTaskId = :clickupTaskId AND t.syncEnabled = true")
    Optional<TaskMapping> findActiveMappingByClickupTaskId(String clickupTaskId);
}
