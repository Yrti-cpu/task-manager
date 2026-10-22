package org.yrti.taskmanager.event;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.yrti.taskmanager.config.OutboxProperties;

import java.time.Clock;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class KafkaTaskEventPublisherTest {
    private final KafkaTemplate<String, TaskEvent> template = mock(KafkaTemplate.class);
    private final OutboxProperties properties = new OutboxProperties();
    private final TaskEvent event = TaskEvent.created(11L, 7L, "test", null, null, null, Clock.systemUTC());
    private final KafkaTaskEventPublisher publisher = new KafkaTaskEventPublisher(template, "task-events", properties);

    @Test
    void usesTaskKeyAndWaitsForAcknowledgement() {
        when(template.send("task-events", "11", event)).thenReturn(CompletableFuture.completedFuture(null));
        publisher.publish(event);
        verify(template).send("task-events", "11", event);
    }

    @Test
    void boundsAcknowledgementWait() throws Exception {
        CompletableFuture<SendResult<String, TaskEvent>> future = mock(CompletableFuture.class);
        when(template.send("task-events", "11", event)).thenReturn(future);
        when(future.get(properties.getSendTimeout().toMillis(), TimeUnit.MILLISECONDS)).thenThrow(new TimeoutException());
        assertThatThrownBy(() -> publisher.publish(event)).isInstanceOf(IllegalStateException.class).hasCauseInstanceOf(TimeoutException.class);
    }

    @Test
    void propagatesBrokerFailure() {
        when(template.send("task-events", "11", event)).thenReturn(CompletableFuture.failedFuture(new IllegalArgumentException("broker")));
        assertThatThrownBy(() -> publisher.publish(event)).isInstanceOf(IllegalStateException.class).hasCauseInstanceOf(IllegalArgumentException.class);
    }
}
