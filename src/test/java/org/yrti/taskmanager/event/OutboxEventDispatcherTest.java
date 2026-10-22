package org.yrti.taskmanager.event;

import org.junit.jupiter.api.Test;
import org.yrti.taskmanager.config.OutboxProperties;
import org.yrti.taskmanager.model.OutboxEvent;
import org.yrti.taskmanager.service.OutboxEventService;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OutboxEventDispatcherTest {
    private final OutboxEventService service = mock(OutboxEventService.class);
    private final TaskEventPublisher publisher = mock(TaskEventPublisher.class);
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final OutboxProperties properties = new OutboxProperties();
    private final OutboxEventDispatcher dispatcher = new OutboxEventDispatcher(service, publisher, mapper, properties);

    @Test
    void claimsJustBeforeEachSendAndRespectsBatchSize() {
        OutboxEvent event = event();
        properties.setBatchSize(2);
        when(service.claim(1)).thenReturn(List.of(event));
        when(service.markPublished(event)).thenReturn(true);
        assertThat(dispatcher.dispatchPending()).isEqualTo(2);
        var order = inOrder(service, publisher);
        for (int i = 0; i < 2; i++) {
            order.verify(service).claim(1);
            order.verify(publisher).publish(any());
            order.verify(service).markPublished(event);
        }
    }

    @Test
    void retriesPublisherFailuresAndDoesNotMarkPublished() {
        OutboxEvent event = event();
        when(service.claim(1)).thenReturn(List.of(event), List.of());
        doThrow(new IllegalStateException("unavailable")).when(publisher).publish(any());
        assertThat(dispatcher.dispatchPending()).isZero();
        verify(service).markFailed(eq(event), any(IllegalStateException.class));
        verify(service, never()).markPublished(any());
    }

    @Test
    void malformedPayloadDoesNotReachKafka() {
        OutboxEvent event = OutboxEvent.builder().payload("{broken").build();
        when(service.claim(1)).thenReturn(List.of(event), List.of());
        assertThat(dispatcher.dispatchPending()).isZero();
        verify(service).markFailed(eq(event), any(RuntimeException.class));
        verifyNoInteractions(publisher);
    }

    private OutboxEvent event() {
        return OutboxEvent.builder().payload(mapper.writeValueAsString(
                TaskEvent.created(11L, 7L, "test", null, null, null, Clock.systemUTC()))).build();
    }
}
