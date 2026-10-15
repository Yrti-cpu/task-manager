package org.yrti.taskmanager.event;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.yrti.taskmanager.model.enums.Category;
import org.yrti.taskmanager.model.enums.Status;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class KafkaTaskEventPublisherTest {

    @Test
    void publishesEventUsingTaskKeyAndConfiguredTopic() {
        KafkaTemplate<String, TaskEvent> kafkaTemplate = mock(KafkaTemplate.class);
        TaskEvent event = TaskEvent.created(11L, 7L, "Write tests", Status.IN_PROGRESS,
                Category.WORK, Instant.parse("2026-10-20T12:00:00Z"));

        new KafkaTaskEventPublisher(kafkaTemplate, "task-events").publish(event);

        verify(kafkaTemplate).send(eq("task-events"), eq("11"), sameEventWith(
                "TASK_CREATED", 11L, 7L, "Write tests", Status.IN_PROGRESS,
                Category.WORK, event.dueAt()));
    }

    private static TaskEvent sameEventWith(String eventType, Long taskId, Long recipientUserId,
                                           String taskName, Status status, Category category,
                                           Instant dueAt) {
        return org.mockito.ArgumentMatchers.argThat(event ->
                eventType.equals(event.eventType())
                        && eventIdMatches(event)
                        && event.eventVersion() == TaskEvent.CURRENT_VERSION
                        && taskId.equals(event.taskId())
                        && recipientUserId.equals(event.recipientUserId())
                        && taskName.equals(event.taskName())
                        && status == event.status()
                        && category == event.category()
                        && dueAt.equals(event.dueAt()));
    }

    private static boolean eventIdMatches(TaskEvent event) {
        return event.eventId() != null && event.occurredAt() != null;
    }
}
