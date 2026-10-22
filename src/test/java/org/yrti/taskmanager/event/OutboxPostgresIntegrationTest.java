package org.yrti.taskmanager.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.yrti.taskmanager.dao.OutboxEventRepository;
import org.yrti.taskmanager.model.OutboxEvent;
import org.yrti.taskmanager.service.OutboxEventService;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"spring.docker.compose.enabled=false", "app.cache.redis.enabled=false", "app.outbox.dispatcher.enabled=false"})
@Testcontainers
class OutboxPostgresIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired OutboxEventRepository repository;
    @Autowired OutboxEventService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void clearOutbox() {
        repository.deleteAll();
    }

    @Test
    void skipLockedDoesNotClaimSameEventOrItsSuccessor() throws Exception {
        insert(11, "PENDING", 0);
        insert(11, "PENDING", 0);
        long independent = insert(22, "PENDING", 0);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var transaction = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                var result = repository.lockClaimable(Instant.now(), 1);
                locked.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to release row lock");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                return result.getFirst().getId();
            }));
            try {
                assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
                var claimed = service.claim(100);
                assertThat(claimed).extracting(OutboxEvent::getId).containsExactly(independent);
            } finally {
                release.countDown();
            }
            assertThat(transaction.get(10, TimeUnit.SECONDS)).isNotNull();
        }
    }

    @Test
    void expiredLeaseIsReclaimedAndOldWorkerCannotCompleteOrFail() {
        long first = insert(11, "PENDING", 0);
        long second = insert(11, "PENDING", 0);
        OutboxEvent old = service.claim(1).getFirst();
        assertThat(old.getId()).isEqualTo(first);
        assertThat(service.claim(1)).isEmpty();
        jdbc.update("UPDATE outbox_events SET lease_until = now() - interval '1 second' WHERE id = ?", first);
        OutboxEvent replacement = service.claim(1).getFirst();
        assertThat(replacement.getClaimToken()).isNotEqualTo(old.getClaimToken());
        assertThat(service.markPublished(old)).isFalse();
        assertThat(service.markFailed(old, new IllegalStateException("stale"))).isFalse();
        assertThat(service.markPublished(replacement)).isTrue();
        assertThat(service.claim(1)).extracting(OutboxEvent::getId).containsExactly(second);
    }

    @Test
    void failedPredecessorBlocksSuccessorsAndOnlyPublishedRowsAreCleaned() {
        long failed = insert(11, "PROCESSING", 10);
        jdbc.update("UPDATE outbox_events SET lease_until = now() - interval '1 second' WHERE id = ?", failed);
        insert(11, "PENDING", 0);
        assertThat(service.claim(100)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT status FROM outbox_events WHERE id = ?", String.class, failed)).isEqualTo("FAILED");
        assertThat(service.claim(100)).isEmpty();
        long published = insert(22, "PUBLISHED", 1);
        jdbc.update("UPDATE outbox_events SET published_at = now() - interval '8 days' WHERE id = ?", published);
        assertThat(service.cleanupPublished()).isEqualTo(1);
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void retryBackoffPreventsImmediateReclaim() {
        insert(11, "PENDING", 0);
        OutboxEvent claimed = service.claim(1).getFirst();
        assertThat(service.markFailed(claimed, new IllegalStateException("broker down"))).isTrue();
        assertThat(service.claim(1)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT attempts FROM outbox_events WHERE id = ?", Integer.class, claimed.getId())).isEqualTo(1);
    }

    private long insert(long aggregateId, String status, int attempts) {
        return jdbc.queryForObject("""
                INSERT INTO outbox_events (event_id, aggregate_id, event_type, event_version, payload,
                    status, attempts, next_attempt_at, created_at)
                VALUES (?, ?, 'TASK_UPDATED', 1, '{}', ?, ?, now() - interval '1 minute', now()) RETURNING id
                """, Long.class, UUID.randomUUID(), aggregateId, status, attempts);
    }
}
