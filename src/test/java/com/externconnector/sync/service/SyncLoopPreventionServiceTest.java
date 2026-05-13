package com.externconnector.sync.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class SyncLoopPreventionServiceTest {

    private SyncLoopPreventionService service;

    @BeforeEach
    void setUp() {
        service = new SyncLoopPreventionService(60, 10_000);
    }

    @Test
    void shouldSkip_notRegistered_returnsFalse() {
        assertThat(service.shouldSkip("CLICKUP", "task1", "done")).isFalse();
    }

    @Test
    void shouldSkip_afterRegister_returnsTrue() {
        service.registerSyncOrigin("CLICKUP", "task1", "done");
        assertThat(service.shouldSkip("CLICKUP", "task1", "done")).isTrue();
    }

    @Test
    void shouldSkip_differentPlatform_returnsFalse() {
        service.registerSyncOrigin("CLICKUP", "task1", "done");
        assertThat(service.shouldSkip("LINEAR", "task1", "done")).isFalse();
    }

    @Test
    void shouldSkip_differentTaskId_returnsFalse() {
        service.registerSyncOrigin("CLICKUP", "task1", "done");
        assertThat(service.shouldSkip("CLICKUP", "task2", "done")).isFalse();
    }

    @Test
    void shouldSkip_differentStatus_returnsFalse() {
        service.registerSyncOrigin("CLICKUP", "task1", "done");
        assertThat(service.shouldSkip("CLICKUP", "task1", "in progress")).isFalse();
    }

    @Test
    void shouldSkip_expiredTtl_returnsFalse() throws Exception {
        SyncLoopPreventionService shortTtl = new SyncLoopPreventionService(1, 10_000);
        shortTtl.registerSyncOrigin("CLICKUP", "task1", "done");
        Thread.sleep(1100); // Wait for TTL to expire
        assertThat(shortTtl.shouldSkip("CLICKUP", "task1", "done")).isFalse();
    }

    @Test
    void releaseLock_removesLock() {
        service.registerSyncOrigin("CLICKUP", "task1", "done");
        assertThat(service.shouldSkip("CLICKUP", "task1", "done")).isTrue();
        service.releaseLock("CLICKUP", "task1", "done");
        assertThat(service.shouldSkip("CLICKUP", "task1", "done")).isFalse();
    }

    @Test
    void caseInsensitive_statusNormalization() {
        service.registerSyncOrigin("CLICKUP", "task1", "In Progress");
        assertThat(service.shouldSkip("CLICKUP", "task1", "in progress")).isTrue();
        assertThat(service.shouldSkip("CLICKUP", "task1", "IN PROGRESS")).isTrue();
    }

    @Test
    void activeLockCount_tracksCorrectly() {
        assertThat(service.activeLockCount()).isEqualTo(0);
        service.registerSyncOrigin("CLICKUP", "task1", "done");
        service.registerSyncOrigin("LINEAR", "issue1", "Done");
        assertThat(service.activeLockCount()).isEqualTo(2);
    }

    @Test
    void cleanupExpiredLocks_removesExpiredEntries() throws Exception {
        SyncLoopPreventionService shortTtl = new SyncLoopPreventionService(1, 10_000);
        shortTtl.registerSyncOrigin("CLICKUP", "task1", "done");
        shortTtl.registerSyncOrigin("CLICKUP", "task2", "todo");
        Thread.sleep(1100);
        shortTtl.cleanupExpiredLocks();
        assertThat(shortTtl.activeLockCount()).isEqualTo(0);
    }

    @Test
    void maxCacheSize_preventsUnboundedGrowth() {
        SyncLoopPreventionService bounded = new SyncLoopPreventionService(60, 10);
        // Fill beyond max
        for (int i = 0; i < 20; i++) {
            bounded.registerSyncOrigin("CLICKUP", "task" + i, "done");
        }
        assertThat(bounded.activeLockCount()).isLessThanOrEqualTo(20);
    }

    @RepeatedTest(5)
    void concurrentRegistrations_noDataRace() throws Exception {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger errors = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    latch.await();
                    service.registerSyncOrigin("CLICKUP", "concurrent-task-" + idx, "done");
                    service.shouldSkip("CLICKUP", "concurrent-task-" + idx, "done");
                } catch (Exception e) {
                    errors.incrementAndGet();
                }
            });
        }

        latch.countDown();
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertThat(errors.get()).isEqualTo(0);
    }
}
