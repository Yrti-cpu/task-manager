package org.yrti.taskmanager.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.yrti.taskmanager.dao.OutboxEventRepository;
import org.yrti.taskmanager.event.TaskEvent;
import org.yrti.taskmanager.model.OutboxEvent;
import org.yrti.taskmanager.model.Task;
import org.yrti.taskmanager.model.enums.OutboxEventStatus;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class OutboxEventService {

    private static final Duration RETRY_DELAY = Duration.ofSeconds(30);

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public OutboxEvent enqueueCreated(Task task) {
        return enqueue(TaskEvent.created(task.getId(), task.getOwner().getId(), task.getName(),
                task.getStatus(), task.getCategory(), task.getDueAt()));
    }

    @Transactional
    public OutboxEvent enqueueUpdated(Task task) {
        return enqueue(TaskEvent.updated(task.getId(), task.getOwner().getId(), task.getName(),
                task.getStatus(), task.getCategory(), task.getDueAt()));
    }

    @Transactional
    public OutboxEvent enqueue(TaskEvent event) {
        try {
            return outboxEventRepository.save(OutboxEvent.builder()
                    .eventId(event.eventId())
                    .eventType(event.eventType())
                    .eventVersion(event.eventVersion())
                    .payload(objectMapper.writeValueAsString(event))
                    .status(OutboxEventStatus.PENDING)
                    .attempts(0)
                    .nextAttemptAt(event.occurredAt())
                    .createdAt(event.occurredAt())
                    .build());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize task event " + event.eventId(), exception);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAttempt(OutboxEvent event, Instant now) {
        event.setAttempts(event.getAttempts() + 1);
        event.setNextAttemptAt(now);
        outboxEventRepository.save(event);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublished(OutboxEvent event, Instant now) {
        event.setStatus(OutboxEventStatus.PUBLISHED);
        event.setPublishedAt(now);
        event.setLastError(null);
        outboxEventRepository.save(event);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(OutboxEvent event, Instant now, Exception exception) {
        event.setStatus(OutboxEventStatus.PENDING);
        event.setNextAttemptAt(now.plus(RETRY_DELAY));
        event.setLastError(trimError(exception));
        outboxEventRepository.save(event);
    }

    private String trimError(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return message.length() > 2000 ? message.substring(0, 2000) : message;
    }
}
