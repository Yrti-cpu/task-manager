package org.yrti.taskmanager.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.yrti.taskmanager.config.OutboxProperties;
import org.yrti.taskmanager.model.OutboxEvent;
import org.yrti.taskmanager.service.OutboxEventService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.outbox.dispatcher.enabled", havingValue = "true")
public class OutboxEventDispatcher {
    private final OutboxEventService outboxEventService;
    private final TaskEventPublisher taskEventPublisher;
    private final JsonMapper objectMapper;
    private final OutboxProperties properties;

    @Scheduled(fixedDelayString = "${app.outbox.dispatcher.fixed-delay-ms:5000}")
    public void dispatchScheduled() {
        dispatchPending();
    }

    public int dispatchPending() {
        int published = 0;
        // Claim just before sending so queued events cannot exhaust their leases while waiting on Kafka.
        for (int i = 0; i < properties.getBatchSize() && !Thread.currentThread().isInterrupted(); i++) {
            var events = outboxEventService.claim(1);
            if (events.isEmpty()) {
                break;
            }
            if (dispatch(events.getFirst())) {
                published++;
            }
        }
        return published;
    }

    @Scheduled(fixedDelayString = "${app.outbox.dispatcher.cleanup-delay-ms:3600000}")
    public void cleanupPublished() {
        outboxEventService.cleanupPublished();
    }

    private boolean dispatch(OutboxEvent outboxEvent) {
        TaskEvent event;
        try {
            event = objectMapper.readValue(outboxEvent.getPayload(), TaskEvent.class);
            if (event == null || event.eventId() == null || event.taskId() == null) {
                throw new IllegalArgumentException("Outbox payload must contain eventId and taskId");
            }
        } catch (JacksonException exception) {
            recordFailure(outboxEvent, exception, true);
            return false;
        } catch (IllegalArgumentException exception) {
            recordFailure(outboxEvent, exception, true);
            return false;
        }

        try {
            taskEventPublisher.publish(event);
            boolean published = outboxEventService.markPublished(outboxEvent);
            if (!published) {
                log.warn("Lost claim while marking outbox event {} as published", outboxEvent.getEventId());
            }
            return published;
        } catch (RuntimeException exception) {
            recordFailure(outboxEvent, exception, false);
            return false;
        }
    }

    private void recordFailure(OutboxEvent event, RuntimeException exception, boolean permanent) {
        log.warn("Could not dispatch outbox event {} (permanent={})", event.getEventId(), permanent, exception);
        try {
            boolean recorded = permanent
                    ? outboxEventService.markPermanentlyFailed(event, exception)
                    : outboxEventService.markFailed(event, exception);
            if (!recorded) {
                log.warn("Lost claim while recording failure for outbox event {}", event.getEventId());
            }
        } catch (RuntimeException persistenceException) {
            log.error("Could not persist failure for outbox event {}; lease recovery will retry it",
                    event.getEventId(), persistenceException);
        }
    }
}
