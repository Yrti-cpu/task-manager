package org.yrti.taskmanager.event;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.yrti.taskmanager.model.Task;

@Component
public class KafkaTaskEventPublisher implements TaskEventPublisher {

    private final KafkaTemplate<String, TaskEvent> kafkaTemplate;
    private final String taskEventsTopic;

    public KafkaTaskEventPublisher(
            KafkaTemplate<String, TaskEvent> kafkaTemplate,
            @Value("${app.kafka.topics.task-events}") String taskEventsTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.taskEventsTopic = taskEventsTopic;
    }

    @Override
    public void publishCreated(Task task) {
        publish(TaskEvent.created(task.getId(), task.getOwner().getId(), task.getName(),
                task.getStatus(), task.getCategory(), task.getDueAt()));
    }

    @Override
    public void publishUpdated(Task task) {
        publish(TaskEvent.updated(task.getId(), task.getOwner().getId(), task.getName(),
                task.getStatus(), task.getCategory(), task.getDueAt()));
    }

    private void publish(TaskEvent event) {
        kafkaTemplate.send(taskEventsTopic, event.taskId().toString(), event);
    }
}
