package org.yrti.taskmanager.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yrti.taskmanager.dao.OutboxEventRepository;
import org.yrti.taskmanager.model.OutboxEvent;
import org.yrti.taskmanager.model.enums.OutboxEventStatus;
import org.yrti.taskmanager.service.OutboxEventService;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxEventDispatcherTest {

    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    private final OutboxEventService outboxService = mock(OutboxEventService.class);
    private final TaskEventPublisher publisher = mock(TaskEventPublisher.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-15T09:00:00Z"), ZoneOffset.UTC);
    private OutboxEventDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new OutboxEventDispatcher(repository, outboxService, publisher,
                new ObjectMapper().findAndRegisterModules(), clock);
    }

    @Test
    void publishesPendingEventAndMarksItPublished() {
        OutboxEvent event = pendingEvent();
        when(repository.findTop100ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                OutboxEventStatus.PENDING, clock.instant())).thenReturn(List.of(event));

        assertThat(dispatcher.dispatchPending()).isEqualTo(1);

        verify(outboxService).markAttempt(event, clock.instant());
        verify(publisher).publish(any(TaskEvent.class));
        verify(outboxService).markPublished(event, clock.instant());
    }

    @Test
    void keepsEventPendingAndRecordsFailureWhenPublishingFails() {
        OutboxEvent event = pendingEvent();
        when(repository.findTop100ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                OutboxEventStatus.PENDING, clock.instant())).thenReturn(List.of(event));
        doThrow(new IllegalStateException("Kafka unavailable")).when(publisher).publish(any(TaskEvent.class));

        assertThat(dispatcher.dispatchPending()).isZero();

        verify(outboxService).markAttempt(event, clock.instant());
        verify(outboxService).markFailed(any(OutboxEvent.class), any(Instant.class), any(IllegalStateException.class));
    }

    private OutboxEvent pendingEvent() {
        TaskEvent taskEvent = TaskEvent.created(11L, 7L, "Write tests", null, null, null);
        try {
            return OutboxEvent.builder()
                    .eventId(taskEvent.eventId())
                    .eventType(taskEvent.eventType())
                    .eventVersion(taskEvent.eventVersion())
                    .payload(new ObjectMapper().findAndRegisterModules().writeValueAsString(taskEvent))
                    .status(OutboxEventStatus.PENDING)
                    .nextAttemptAt(clock.instant())
                    .createdAt(clock.instant())
                    .build();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
