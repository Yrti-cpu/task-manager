package org.yrti.taskmanager.service;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.yrti.taskmanager.dao.TaskRepository;
import org.yrti.taskmanager.dao.UserRepository;
import org.yrti.taskmanager.dto.TaskCreateRequest;
import org.yrti.taskmanager.dto.TaskDTO;
import org.yrti.taskmanager.dto.TaskPageResponse;
import org.yrti.taskmanager.dto.TaskUpdateRequest;
import org.yrti.taskmanager.mapper.TaskMapper;
import org.yrti.taskmanager.model.Task;
import org.yrti.taskmanager.model.User;
import org.yrti.taskmanager.model.enums.Category;
import org.yrti.taskmanager.model.enums.Status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

        InOrder order = inOrder(taskRepository, outboxEventService);
        order.verify(taskRepository).save(task);
        order.verify(outboxEventService).enqueueCreated(saved);
    }

    @Test
    void updateSavesChangedTaskBeforePublishingUpdatedEvent() {
        TaskRepository taskRepository = mock(TaskRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        TaskMapper taskMapper = mock(TaskMapper.class);
        OutboxEventService outboxEventService = mock(OutboxEventService.class);
        TaskService service = new TaskService(taskRepository, userRepository, taskMapper, outboxEventService);

        Task task = Task.builder().id(11L).build();
        TaskUpdateRequest request = new TaskUpdateRequest(
                "Updated", Status.COMPLETED, Category.WORK,
                Instant.parse("2026-10-20T12:00:00Z"));
        TaskDTO dto = new TaskDTO(11L, "Updated", Status.COMPLETED, Category.WORK,
                request.dueAt(), 7L, "owner", null, null);

        when(taskRepository.findById(11L)).thenReturn(Optional.of(task));
        when(taskRepository.save(task)).thenReturn(task);
        when(taskMapper.toDto(task)).thenReturn(dto);

        assertSame(dto, service.updateTask(11L, request));

        InOrder order = inOrder(taskRepository, taskMapper, outboxEventService);
        order.verify(taskRepository).findById(11L);
        order.verify(taskMapper).updateEntity(task, request);
        order.verify(taskRepository).save(task);
        order.verify(outboxEventService).enqueueUpdated(task);
    }

    @Test
    void doesNotEnqueueCreatedEventWhenOwnerIsMissing() {
        TaskRepository taskRepository = mock(TaskRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        TaskMapper taskMapper = mock(TaskMapper.class);
        OutboxEventService outboxEventService = mock(OutboxEventService.class);
        TaskService service = new TaskService(taskRepository, userRepository, taskMapper, outboxEventService);
        TaskCreateRequest request = new TaskCreateRequest(
                "Write tests", Status.IN_PROGRESS, Category.WORK, 7L,
                Instant.parse("2026-10-20T12:00:00Z"));
        when(userRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createTask(request))
                .isInstanceOf(org.yrti.taskmanager.exception.ResourceNotFoundException.class);

        verify(taskRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(outboxEventService, never()).enqueueCreated(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void doesNotEnqueueUpdatedEventWhenTaskIsMissing() {
        TaskRepository taskRepository = mock(TaskRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        TaskMapper taskMapper = mock(TaskMapper.class);
        OutboxEventService outboxEventService = mock(OutboxEventService.class);
        TaskService service = new TaskService(taskRepository, userRepository, taskMapper, outboxEventService);
        when(taskRepository.findById(11L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateTask(11L, new TaskUpdateRequest(
                "Updated", Status.COMPLETED, Category.WORK,
                Instant.parse("2026-10-20T12:00:00Z"))))
                .isInstanceOf(org.yrti.taskmanager.exception.ResourceNotFoundException.class);

        verify(taskRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(outboxEventService, never()).enqueueUpdated(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void searchUsesAllFiltersAndStableSort() {
        TaskRepository taskRepository = mock(TaskRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        TaskMapper taskMapper = mock(TaskMapper.class);
        OutboxEventService outboxEventService = mock(OutboxEventService.class);
        TaskService service = new TaskService(taskRepository, userRepository, taskMapper, outboxEventService);

        Task task = Task.builder().id(11L).build();
        TaskDTO dto = new TaskDTO(11L, "Write tests", Status.COMPLETED, Category.WORK,
                Instant.parse("2026-10-20T12:00:00Z"), 7L, "owner", null, null);
        Instant from = Instant.parse("2026-10-01T00:00:00Z");
        Instant to = Instant.parse("2026-10-31T23:59:59Z");
        PageRequest request = PageRequest.of(1, 2, Sort.by(Sort.Order.asc("dueAt")));

        when(taskRepository.search(Status.COMPLETED, from, to,
                PageRequest.of(1, 2, Sort.by(
                        Sort.Order.asc("dueAt"),
                        Sort.Order.asc("createdAt"),
                        Sort.Order.asc("id")))))
                .thenReturn(new PageImpl<>(List.of(task), request, 5));
        when(taskMapper.toDto(task)).thenReturn(dto);

        TaskPageResponse response = service.searchTasks(Status.COMPLETED, from, to, request);

        org.junit.jupiter.api.Assertions.assertEquals(List.of(dto), response.content());
        org.junit.jupiter.api.Assertions.assertEquals(1, response.page());
        org.junit.jupiter.api.Assertions.assertEquals(2, response.size());
        org.junit.jupiter.api.Assertions.assertEquals(5, response.totalElements());
        org.junit.jupiter.api.Assertions.assertEquals(3, response.totalPages());
        verify(taskRepository).search(Status.COMPLETED, from, to,
                PageRequest.of(1, 2, Sort.by(
                        Sort.Order.asc("dueAt"),
                        Sort.Order.asc("createdAt"),
                        Sort.Order.asc("id"))));
    }

    @Test
    void searchPreservesPageMetadataWhenFiltersAreNull() {
        TaskRepository taskRepository = mock(TaskRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        TaskMapper taskMapper = mock(TaskMapper.class);
        OutboxEventService outboxEventService = mock(OutboxEventService.class);
        TaskService service = new TaskService(taskRepository, userRepository, taskMapper, outboxEventService);
        PageRequest request = PageRequest.of(0, 20);
        when(taskRepository.search(null, null, null, PageRequest.of(0, 20, Sort.by(
                Sort.Order.asc("dueAt"),
                Sort.Order.asc("createdAt"),
                Sort.Order.asc("id")))))
                .thenReturn(new PageImpl<>(List.of(), request, 0));

        TaskPageResponse response = service.searchTasks(null, null, null, request);

        org.junit.jupiter.api.Assertions.assertAll(
                () -> org.junit.jupiter.api.Assertions.assertTrue(response.content().isEmpty()),
                () -> org.junit.jupiter.api.Assertions.assertEquals(0, response.page()),
                () -> org.junit.jupiter.api.Assertions.assertEquals(20, response.size()),
                () -> org.junit.jupiter.api.Assertions.assertEquals(0, response.totalElements()),
                () -> org.junit.jupiter.api.Assertions.assertEquals(0, response.totalPages()),
                () -> org.junit.jupiter.api.Assertions.assertTrue(response.first()),
                () -> org.junit.jupiter.api.Assertions.assertTrue(response.last()));
    }

    @Test
    void createAndUpdateMethodsAreTransactional() throws NoSuchMethodException {
        org.junit.jupiter.api.Assertions.assertNotNull(
                TaskService.class.getMethod("createTask", TaskCreateRequest.class)
                        .getAnnotation(Transactional.class));
        org.junit.jupiter.api.Assertions.assertNotNull(
                TaskService.class.getMethod("updateTask", Long.class, TaskUpdateRequest.class)
                        .getAnnotation(Transactional.class));
    }
}
