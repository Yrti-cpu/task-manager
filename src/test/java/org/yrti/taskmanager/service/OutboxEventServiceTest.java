package org.yrti.taskmanager.service;

import org.junit.jupiter.api.Test;
import org.yrti.taskmanager.config.OutboxProperties;
import org.yrti.taskmanager.dao.OutboxEventRepository;
import org.yrti.taskmanager.event.TaskEvent;
import org.yrti.taskmanager.model.OutboxEvent;
import org.yrti.taskmanager.model.enums.OutboxEventStatus;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboxEventServiceTest {
    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-15T09:00:00Z"), ZoneOffset.UTC);
    private final OutboxProperties properties = new OutboxProperties();
    private final OutboxEventService service = new OutboxEventService(repository, JsonMapper.builder().build(), clock, properties);

    @Test
    void storesEventIdentityAggregateAndJsonPayloadUsingClock() {
        // given
        TaskEvent event = TaskEvent.created(11L, 7L, "Write tests", null, null, null, clock);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        // when
        OutboxEvent saved = service.enqueue(event);
        // then
        assertThat(saved.getEventId()).isEqualTo(event.eventId());
        assertThat(saved.getAggregateId()).isEqualTo(11L);
        assertThat(saved.getPayload()).contains(event.eventId().toString(), "\"taskId\":11");
        assertThat(saved.getCreatedAt()).isEqualTo(clock.instant());
        assertThat(saved.getNextAttemptAt()).isEqualTo(clock.instant());
    }

    @Test
    void claimsWithNewOwnershipAndIncrementsAttempts() {
        // given
        OutboxEvent event = OutboxEvent.builder().attempts(1).claimToken(UUID.randomUUID()).build();
        UUID oldToken = event.getClaimToken();
        when(repository.lockClaimable(clock.instant(), 1)).thenReturn(List.of(event));
        // when
        assertThat(service.claim(1)).containsExactly(event);
        // then
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PROCESSING);
        assertThat(event.getClaimToken()).isNotEqualTo(oldToken);
        assertThat(event.getAttempts()).isEqualTo(2);
        assertThat(event.getLeaseUntil()).isEqualTo(clock.instant().plus(properties.getLeaseDuration()));
    }

    @Test
    void exhaustedLeaseBecomesFailedAndIsNotSentAgain() {
        // given
        OutboxEvent event = OutboxEvent.builder().attempts(properties.getMaxAttempts()).build();
        when(repository.lockClaimable(clock.instant(), 1)).thenReturn(List.of(event));
        // when
        assertThat(service.claim(1)).isEmpty();
        // then
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(event.getLeaseUntil()).isNull();
    }

    @Test
    void failureUsesExponentialBackoffAndTruncatesErrors() {
        // given
        OutboxEvent event = OutboxEvent.builder().id(1L).claimToken(UUID.randomUUID()).attempts(3).build();
        // when
        service.markFailed(event, new IllegalStateException("x".repeat(2100)));
        // then
        verify(repository).failOwned(eq(1L), eq(event.getClaimToken()), eq(clock.instant()), eq("PENDING"),
                eq(clock.instant().plusSeconds(120)), eq("x".repeat(2000)));
        // given
        event.setAttempts(properties.getMaxAttempts());
        // when
        service.markFailed(event, new IllegalStateException("failed"));
        // then
        verify(repository).failOwned(eq(1L), eq(event.getClaimToken()), eq(clock.instant()), eq("FAILED"),
                eq(clock.instant().plus(properties.getMaxBackoff())), eq("failed"));
    }

    @Test
    void staleCompletionDoesNotCountAsPublishedAndCleanupUsesRetention() {
        // given
        OutboxEvent event = OutboxEvent.builder().id(1L).claimToken(UUID.randomUUID()).build();
        // when
        assertThat(service.markPublished(event)).isFalse();
        // then
        verify(repository).publishOwned(1L, event.getClaimToken(), clock.instant());
        // when
        service.cleanupPublished();
        // then
        verify(repository).deletePublishedBefore(clock.instant().minus(properties.getRetention()));
    }
}
