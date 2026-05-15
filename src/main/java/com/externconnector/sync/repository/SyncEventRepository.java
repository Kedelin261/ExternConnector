package com.externconnector.sync.repository;

import com.externconnector.sync.entity.SyncEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface SyncEventRepository extends JpaRepository<SyncEvent, Long> {

    Page<SyncEvent> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<SyncEvent> findByTaskMappingIdOrderByCreatedAtDesc(Long taskMappingId);

    @Query("SELECT s FROM SyncEvent s WHERE s.status = 'FAILED' AND s.createdAt > :since ORDER BY s.createdAt DESC")
    List<SyncEvent> findRecentFailures(OffsetDateTime since);

    @Query("SELECT COUNT(s) FROM SyncEvent s WHERE s.status = 'FAILED' AND s.createdAt > :since")
    long countRecentFailures(OffsetDateTime since);

    @Modifying
    @Query("DELETE FROM SyncEvent s WHERE s.createdAt < :cutoff")
    int deleteOlderThan(OffsetDateTime cutoff);
}
