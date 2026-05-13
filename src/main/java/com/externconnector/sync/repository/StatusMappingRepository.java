package com.externconnector.sync.repository;

import com.externconnector.sync.entity.StatusMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StatusMappingRepository extends JpaRepository<StatusMapping, Long> {

    @Query("""
        SELECT s FROM StatusMapping s
        WHERE s.linearStatus = :linearStatus
          AND (s.direction = 'BOTH' OR s.direction = 'LINEAR_TO_CLICKUP')
        ORDER BY s.id
        LIMIT 1
    """)
    Optional<StatusMapping> findByLinearStatus(String linearStatus);

    @Query("""
        SELECT s FROM StatusMapping s
        WHERE s.clickupStatus = :clickupStatus
          AND (s.direction = 'BOTH' OR s.direction = 'CLICKUP_TO_LINEAR')
        ORDER BY s.id
        LIMIT 1
    """)
    Optional<StatusMapping> findByClickupStatus(String clickupStatus);

    List<StatusMapping> findAllByOrderByLinearStatusAsc();

    boolean existsByLinearStatusAndClickupStatus(String linearStatus, String clickupStatus);
}
