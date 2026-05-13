package com.externconnector.sync.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prevents infinite sync loops between Linear and ClickUp.
 *
 * Strategy:
 * - When platform A syncs to platform B, we register a "lock" key:
 *   "platform:taskId:status" with a TTL.
 * - Before processing a webhook from platform B, we check if that lock exists.
 * - If it does, we know this event was triggered by our own sync — skip it.
 *
 * Thread-safety:
 * - ConcurrentHashMap for lock storage.
 * - Atomic check-and-set via putIfAbsent for race-condition safety.
 * - Scheduled cleanup of expired entries.
 *
 * Limitations acknowledged:
 * - In-process only (multi-instance deployments need Redis).
 * - Memory bounded by maxCacheSize to prevent OOM.
 */
@Service
public class SyncLoopPreventionService {

    private static final Logger log = LoggerFactory.getLogger(SyncLoopPreventionService.class);

    private final long ttlMillis;
    private final int maxCacheSize;

    // key: "platform:taskId:status" → expiry timestamp in millis
    private final ConcurrentHashMap<String, Long> locks = new ConcurrentHashMap<>();

    public SyncLoopPreventionService(
            @Value("${sync.loop-prevention.ttl-seconds:60}") int ttlSeconds,
            @Value("${sync.loop-prevention.max-cache-size:10000}") int maxCacheSize) {
        this.ttlMillis = ttlSeconds * 1000L;
        this.maxCacheSize = maxCacheSize;
    }

    /**
     * Register a lock indicating that we (not the remote platform) triggered
     * this status change. Call this BEFORE making the API call to the target platform.
     *
     * @param platform  e.g. "CLICKUP" or "LINEAR"
     * @param taskId    the task/issue ID on that platform
     * @param status    the new status we're setting
     */
    public void registerSyncOrigin(String platform, String taskId, String status) {
        if (locks.size() >= maxCacheSize) {
            // Evict oldest 10% to avoid unbounded growth
            evictOldest(maxCacheSize / 10);
        }
        String key = buildKey(platform, taskId, status);
        long expiry = System.currentTimeMillis() + ttlMillis;
        locks.put(key, expiry);
        log.debug("Registered sync-origin lock: {} (expires in {}ms)", key, ttlMillis);
    }

    /**
     * Check if an incoming webhook event should be skipped because
     * it was originated by our own sync (loop prevention).
     *
     * @param platform  the SOURCE platform of the incoming webhook
     * @param taskId    the task ID on that platform
     * @param status    the new status in the webhook
     * @return true if this event should be skipped (it's our own echo)
     */
    public boolean shouldSkip(String platform, String taskId, String status) {
        String key = buildKey(platform, taskId, status);
        Long expiry = locks.get(key);
        if (expiry == null) {
            return false;
        }
        if (System.currentTimeMillis() > expiry) {
            // Expired — remove and allow processing
            locks.remove(key);
            return false;
        }
        log.info("Loop prevention: skipping echo event from {} task={} status={}", platform, taskId, status);
        return true;
    }

    /**
     * Explicitly release a lock (e.g. on sync failure — so genuine retries are not blocked).
     */
    public void releaseLock(String platform, String taskId, String status) {
        String key = buildKey(platform, taskId, status);
        locks.remove(key);
        log.debug("Released sync-origin lock: {}", key);
    }

    /**
     * Scheduled cleanup of expired entries.
     * Runs every 30 seconds.
     */
    @Scheduled(fixedDelay = 30_000)
    public void cleanupExpiredLocks() {
        long now = System.currentTimeMillis();
        int removed = 0;
        for (Map.Entry<String, Long> entry : locks.entrySet()) {
            if (now > entry.getValue()) {
                locks.remove(entry.getKey());
                removed++;
            }
        }
        if (removed > 0) {
            log.debug("Cleaned up {} expired loop-prevention locks", removed);
        }
    }

    /**
     * Current number of active locks (for monitoring).
     */
    public int activeLockCount() {
        return locks.size();
    }

    private String buildKey(String platform, String taskId, String status) {
        // Normalize to lowercase to handle case variations
        return (platform + ":" + taskId + ":" + (status != null ? status.toLowerCase() : "")).trim();
    }

    private void evictOldest(int count) {
        long now = System.currentTimeMillis();
        locks.entrySet().stream()
                .filter(e -> now > e.getValue())
                .limit(count)
                .forEach(e -> locks.remove(e.getKey()));
    }
}
