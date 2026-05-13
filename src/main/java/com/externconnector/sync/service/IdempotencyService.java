package com.externconnector.sync.service;

import com.externconnector.sync.entity.IdempotencyKey;
import com.externconnector.sync.entity.WebhookLog;
import com.externconnector.sync.repository.IdempotencyKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Database-backed idempotency key store.
 * Prevents duplicate processing of retried / replayed webhooks.
 * TTL-based expiry via scheduled cleanup.
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final IdempotencyKeyRepository repository;
    private final long ttlSeconds;

    public IdempotencyService(
            IdempotencyKeyRepository repository,
            @Value("${webhook.idempotency-ttl-seconds:86400}") long ttlSeconds) {
        this.repository = repository;
        this.ttlSeconds = ttlSeconds;
    }

    /**
     * Check if this key has been seen before (already processed).
     */
    @Transactional(readOnly = true)
    public boolean alreadyProcessed(String key, WebhookLog.Platform source) {
        return repository.existsByKeyAndSource(key, source);
    }

    /**
     * Record a key as processed.
     * Uses INSERT IGNORE semantics — if already exists, no-op.
     */
    @Transactional
    public void markProcessed(String key, WebhookLog.Platform source) {
        if (!repository.existsByKeyAndSource(key, source)) {
            IdempotencyKey entity = IdempotencyKey.builder()
                    .key(key)
                    .source(source)
                    .expiresAt(OffsetDateTime.now().plusSeconds(ttlSeconds))
                    .build();
            try {
                repository.save(entity);
                log.debug("Marked idempotency key as processed: {} [{}]", key, source);
            } catch (Exception e) {
                // Race condition: another thread inserted first — safe to ignore
                log.debug("Idempotency key already exists (race condition handled): {} [{}]", key, source);
            }
        }
    }

    /**
     * Scheduled cleanup of expired keys.
     * Runs every hour.
     */
    @Scheduled(fixedDelay = 3_600_000)
    @Transactional
    public void cleanupExpiredKeys() {
        int deleted = repository.deleteExpired(OffsetDateTime.now());
        if (deleted > 0) {
            log.info("Cleaned up {} expired idempotency keys", deleted);
        }
    }
}
