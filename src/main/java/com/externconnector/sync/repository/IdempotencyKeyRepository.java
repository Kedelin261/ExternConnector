package com.externconnector.sync.repository;

import com.externconnector.sync.entity.IdempotencyKey;
import com.externconnector.sync.entity.WebhookLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;

@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {

    boolean existsByKeyAndSource(String key, WebhookLog.Platform source);

    @Modifying
    @Query("DELETE FROM IdempotencyKey i WHERE i.expiresAt < :now")
    int deleteExpired(OffsetDateTime now);
}
