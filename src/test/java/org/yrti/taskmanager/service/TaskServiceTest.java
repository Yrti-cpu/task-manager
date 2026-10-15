package org.yrti.taskmanager.service;

import org.junit.jupiter.api.Test;
import org.yrti.taskmanager.dao.TaskRepository;
import org.yrti.taskmanager.dao.UserRepository;
import org.yrti.taskmanager.dto.TaskCreateRequest;
import org.yrti.taskmanager.dto.TaskDTO;
import org.yrti.taskmanager.mapper.TaskMapper;
import org.yrti.taskmanager.model.Task;
import org.yrti.taskmanager.model.User;
import org.yrti.taskmanager.model.enums.Category;
import org.yrti.taskmanager.model.enums.Status;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskServiceTest {

    @Test
    void publishesCreatedEventOnlyAfterTaskIsSaved() {
        TaskRepository taskRepository = mock(TaskRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        TaskMapper taskMapper = mock(TaskMapper.class);
        OutboxEventService outboxEventService = mock(OutboxEventService.class);
        TaskService service = new TaskService(taskRepository, userRepository, taskMapper, outboxEventService);

        User owner = User.builder().id(7L).build();
        Task task = Task.builder().name("Write tests").owner(owner).build();
        Task saved = Task.builder().id(11L).name("Write tests").owner(owner).build();
        TaskCreateRequest request = new TaskCreateRequest(
                "Write tests", Status.IN_PROGRESS, Category.WORK, owner.getId(),
                Instant.parse("2026-10-20T12:00:00Z"));
        TaskDTO dto = new TaskDTO(11L, "Write tests", Status.IN_PROGRESS, Category.WORK,
                request.dueAt(), owner.getId(), null, null, null);

        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(taskMapper.toEntity(request, owner)).thenReturn(task);
        when(taskRepository.save(task)).thenReturn(saved);
        when(taskMapper.toDto(saved)).thenReturn(dto);

        assertSame(dto, service.createTask(request));

        verify(taskRepository).save(task);
        verify(outboxEventService).enqueueCreated(saved);
    }
}
