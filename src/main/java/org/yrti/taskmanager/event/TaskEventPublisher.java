package org.yrti.taskmanager.event;

public interface TaskEventPublisher {

    void publish(TaskEvent event);
}
