package org.yrti.taskmanager.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.yrti.taskmanager.dao.OutboxEventRepository;
import org.yrti.taskmanager.event.TaskEvent;
import org.yrti.taskmanager.model.OutboxEvent;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxEventServiceTest {

    @Test
    void storesEventIdentityAndJsonPayload() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        OutboxEventService service = new OutboxEventService(repository, new ObjectMapper().findAndRegisterModules());
        TaskEvent event = TaskEvent.created(11L, 7L, "Write tests", null, null,
                Instant.parse("2026-10-20T12:00:00Z"));

        when(repository.save(any(OutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OutboxEvent saved = service.enqueue(event);

        assertThat(saved.getEventId()).isEqualTo(event.eventId());
        assertThat(saved.getEventType()).isEqualTo(event.eventType());
        assertThat(saved.getEventVersion()).isEqualTo(event.eventVersion());
        assertThat(saved.getPayload()).contains(event.eventId().toString(), "\"taskId\":11");
        assertThat(saved.getAttempts()).isZero();
        verify(repository).save(saved);
    }
}
