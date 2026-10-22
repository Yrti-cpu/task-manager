package org.yrti.taskmanager.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.yrti.taskmanager.config.OutboxProperties;
import org.yrti.taskmanager.dao.OutboxEventRepository;
import org.yrti.taskmanager.event.TaskEvent;
import org.yrti.taskmanager.model.OutboxEvent;
import org.yrti.taskmanager.model.Task;
import org.yrti.taskmanager.model.enums.OutboxEventStatus;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxEventService {
    private final OutboxEventRepository outboxEventRepository;
    private final JsonMapper objectMapper;
    private final Clock clock;
    private final OutboxProperties properties;

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent enqueueCreated(Task task) {
        return enqueue(TaskEvent.created(task.getId(), task.getOwner().getId(), task.getName(),
                task.getStatus(), task.getCategory(), task.getDueAt(), clock));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent enqueueUpdated(Task task) {
        return enqueue(TaskEvent.updated(task.getId(), task.getOwner().getId(), task.getName(),
                task.getStatus(), task.getCategory(), task.getDueAt(), clock));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent enqueue(TaskEvent event) {
        return outboxEventRepository.save(OutboxEvent.builder()
                .eventId(event.eventId()).aggregateId(event.taskId())
                .eventType(event.eventType()).eventVersion(event.eventVersion())
                .payload(objectMapper.writeValueAsString(event)).status(OutboxEventStatus.PENDING)
                .attempts(0).nextAttemptAt(clock.instant()).createdAt(event.occurredAt()).build());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<OutboxEvent> claim(int limit) {
        Instant now = clock.instant();
        List<OutboxEvent> claimed = new ArrayList<>();
        for (OutboxEvent event : outboxEventRepository.lockClaimable(now, Math.min(limit, properties.getBatchSize()))) {
            if (event.getAttempts() >= properties.getMaxAttempts()) {
                event.setStatus(OutboxEventStatus.FAILED);
                event.setClaimToken(null);
                event.setLeaseUntil(null);
                event.setLastError("Processing lease expired after final attempt");
                continue;
            }
            event.setStatus(OutboxEventStatus.PROCESSING);
            event.setAttempts(event.getAttempts() + 1);
            event.setClaimToken(UUID.randomUUID());
            event.setLeaseUntil(now.plus(properties.getLeaseDuration()));
            claimed.add(event);
        }
        return claimed;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markPublished(OutboxEvent event) {
        return outboxEventRepository.publishOwned(event.getId(), event.getClaimToken(), clock.instant()) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markFailed(OutboxEvent event, Exception exception) {
        Instant now = clock.instant();
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        String status = event.getAttempts() >= properties.getMaxAttempts() ? "FAILED" : "PENDING";
        return outboxEventRepository.failOwned(event.getId(), event.getClaimToken(), now, status,
                now.plus(backoff(event.getAttempts())), message.substring(0, Math.min(2000, message.length()))) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markPermanentlyFailed(OutboxEvent event, Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        Instant now = clock.instant();
        return outboxEventRepository.failOwned(event.getId(), event.getClaimToken(), now,
                OutboxEventStatus.FAILED.name(), now,
                message.substring(0, Math.min(2000, message.length()))) == 1;
    }

    private Duration backoff(int attempts) {
        Duration delay = properties.getInitialBackoff();
        for (int i = 1; i < attempts && delay.compareTo(properties.getMaxBackoff()) < 0; i++) {
            if (delay.compareTo(properties.getMaxBackoff().dividedBy(2)) > 0) {
                return properties.getMaxBackoff();
            }
            delay = delay.multipliedBy(2);
        }
        return delay.compareTo(properties.getMaxBackoff()) > 0 ? properties.getMaxBackoff() : delay;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int cleanupPublished() {
        return outboxEventRepository.deletePublishedBefore(clock.instant().minus(properties.getRetention()));
    }
}
