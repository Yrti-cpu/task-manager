package org.yrti.taskmanager.event;

import org.yrti.taskmanager.model.Task;

public interface TaskEventPublisher {

    void publishCreated(Task task);

    void publishUpdated(Task task);
}
