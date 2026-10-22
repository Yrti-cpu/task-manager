package org.yrti.taskmanager.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.yrti.taskmanager.dao.OutboxEventRepository;
import org.yrti.taskmanager.event.TaskEvent;
import org.yrti.taskmanager.model.enums.OutboxEventStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "app.cache.redis.enabled=false",
        "app.outbox.dispatcher.enabled=false"
})
@Testcontainers
class OutboxEventServiceTransactionTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired private OutboxEventService service;
    @Autowired private OutboxEventRepository repository;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void requiresExistingTransactionAndPersistsEventAfterCommit() {
        // given
        Clock clock = Clock.fixed(Instant.parse("2026-10-15T09:00:00Z"), ZoneOffset.UTC);
        TaskEvent event = TaskEvent.created(11L, 7L, "Write tests", null, null, null, clock);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        long initialCount = repository.count();

        // when
        assertThatThrownBy(() -> service.enqueue(event))
                // then
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(repository.count()).isEqualTo(initialCount);

        // when
        var saved = transaction.execute(status -> service.enqueue(event));

        // then
        assertThat(saved).isNotNull();
        try {
            var persisted = repository.findById(saved.getId()).orElseThrow();
            assertThat(persisted.getEventId()).isEqualTo(event.eventId());
            assertThat(persisted.getAggregateId()).isEqualTo(event.taskId());
            assertThat(persisted.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
            assertThat(repository.count()).isEqualTo(initialCount + 1);
        } finally {
            repository.deleteById(saved.getId());
        }
    }
}
