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
    public void publish(TaskEvent event) {
        try {
            kafkaTemplate.send(taskEventsTopic, event.taskId().toString(), event).get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing task event " + event.eventId(), exception);
        } catch (java.util.concurrent.ExecutionException exception) {
            throw new IllegalStateException("Could not publish task event " + event.eventId(), exception.getCause());
        }
    }
}
