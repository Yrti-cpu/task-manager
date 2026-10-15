package org.yrti.taskmanager.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.yrti.taskmanager.dao.OutboxEventRepository;
import org.yrti.taskmanager.model.OutboxEvent;
import org.yrti.taskmanager.model.enums.OutboxEventStatus;
import org.yrti.taskmanager.service.OutboxEventService;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.outbox.dispatcher.enabled", havingValue = "true")
public class OutboxEventDispatcher {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventService outboxEventService;
    private final TaskEventPublisher taskEventPublisher;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Value("${app.outbox.dispatcher.batch-size:100}")
    private int batchSize = 100;

    @Scheduled(fixedDelayString = "${app.outbox.dispatcher.fixed-delay-ms:5000}")
    public void dispatchScheduled() {
        dispatchPending();
    }

    public int dispatchPending() {
        Instant now = Instant.now(clock);
        List<OutboxEvent> events = outboxEventRepository
                .findTop100ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                        OutboxEventStatus.PENDING, now)
                .stream()
                .limit(batchSize)
                .toList();
        int published = 0;
        for (OutboxEvent event : events) {
            if (dispatch(event, now)) {
                published++;
            }
        }
        return published;
    }

    private boolean dispatch(OutboxEvent outboxEvent, Instant now) {
        try {
            outboxEventService.markAttempt(outboxEvent, now);
            TaskEvent event = objectMapper.readValue(outboxEvent.getPayload(), TaskEvent.class);
            taskEventPublisher.publish(event);
            outboxEventService.markPublished(outboxEvent, Instant.now(clock));
            return true;
        } catch (JsonProcessingException | RuntimeException exception) {
            outboxEventService.markFailed(outboxEvent, Instant.now(clock), exception);
            log.warn("Could not publish outbox event {}", outboxEvent.getEventId(), exception);
            return false;
        }
    }
}
