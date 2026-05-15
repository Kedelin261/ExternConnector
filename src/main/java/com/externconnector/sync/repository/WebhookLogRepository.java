package com.externconnector.sync.repository;

import com.externconnector.sync.entity.WebhookLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public interface WebhookLogRepository extends JpaRepository<WebhookLog, Long> {

    Page<WebhookLog> findAllByOrderByReceivedAtDesc(Pageable pageable);

    boolean existsByEventIdAndSource(String eventId, WebhookLog.Platform source);

    Optional<WebhookLog> findByEventIdAndSource(String eventId, WebhookLog.Platform source);

    @Query("SELECT COUNT(w) FROM WebhookLog w WHERE w.processed = false AND w.receivedAt < :cutoff")
    long countUnprocessedOlderThan(OffsetDateTime cutoff);

    @Modifying
    @Query("DELETE FROM WebhookLog w WHERE w.receivedAt < :cutoff")
    int deleteOlderThan(OffsetDateTime cutoff);
}
