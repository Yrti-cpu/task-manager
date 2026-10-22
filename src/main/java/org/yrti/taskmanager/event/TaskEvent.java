package org.yrti.taskmanager.event;

import org.yrti.taskmanager.model.enums.Category;
import org.yrti.taskmanager.model.enums.Status;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public record TaskEvent(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        Long taskId,
        Long recipientUserId,
        String taskName,
        Status status,
        Category category,
        Instant dueAt
) {
    public static final int CURRENT_VERSION = 1;

    public static TaskEvent created(Long taskId, Long recipientUserId, String taskName,
                                    Status status, Category category, Instant dueAt, Clock clock) {
        return new TaskEvent(UUID.randomUUID(), "TASK_CREATED", CURRENT_VERSION, clock.instant(),
                taskId, recipientUserId, taskName, status, category, dueAt);
    }

    public static TaskEvent updated(Long taskId, Long recipientUserId, String taskName,
                                    Status status, Category category, Instant dueAt, Clock clock) {
        return new TaskEvent(UUID.randomUUID(), "TASK_UPDATED", CURRENT_VERSION, clock.instant(),
                taskId, recipientUserId, taskName, status, category, dueAt);
    }
}
