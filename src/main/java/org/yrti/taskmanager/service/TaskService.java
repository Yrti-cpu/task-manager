package org.yrti.taskmanager.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yrti.taskmanager.config.CacheNames;
import org.yrti.taskmanager.dao.TaskRepository;
import org.yrti.taskmanager.dao.UserRepository;
import org.yrti.taskmanager.dto.PageResponse;
import org.yrti.taskmanager.dto.TaskCreateRequest;
import org.yrti.taskmanager.dto.TaskResponse;
import org.yrti.taskmanager.dto.TaskUpdateRequest;
import org.yrti.taskmanager.exception.InvalidTaskRequestException;
import org.yrti.taskmanager.exception.ResourceNotFoundException;
import org.yrti.taskmanager.exception.StaleTaskException;
import org.yrti.taskmanager.mapper.TaskMapper;
import org.yrti.taskmanager.model.Task;
import org.yrti.taskmanager.model.User;
import org.yrti.taskmanager.model.enums.Category;
import org.yrti.taskmanager.model.enums.Status;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TaskService {
    private static final Set<String> SORT_FIELDS = Set.of("id", "name", "status", "category", "dueAt", "createdAt", "updatedAt");

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final TaskMapper taskMapper;
    private final OutboxEventService outboxEventService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> getAllTasks(Pageable pageable) {
        return page(taskRepository.findAll(stable(pageable)));
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> searchTasks(Status status, Instant from, Instant to, Pageable pageable) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidTaskRequestException("from must be before or equal to to");
        }
        Page<TaskResponse> result = taskRepository.search(status, from, to, stable(pageable))
                .map(taskMapper::toDto);
        return new PageResponse<>(result);
    }

    @Cacheable(value = CacheNames.TASK, key = "#id")
    @Transactional(readOnly = true)
    public TaskResponse getTaskById(Long id) {
        return taskMapper.toDto(findTask(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> getTasksByStatus(Status status, Pageable pageable) {
        return page(taskRepository.findAllByStatus(status, stable(pageable)));
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> getTasksByCategory(Category category, Pageable pageable) {
        return page(taskRepository.findAllByCategory(category, stable(pageable)));
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> getTasksByOwner(Long ownerId, Pageable pageable) {
        if (!userRepository.existsById(ownerId)) {
            throw new ResourceNotFoundException("User", ownerId);
        }
        return page(taskRepository.findAllByOwnerId(ownerId, stable(pageable)));
    }

    @Transactional
    public TaskResponse createTask(TaskCreateRequest request) {
        User owner = userRepository.findById(request.ownerId())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.ownerId()));
        Task task = taskMapper.toEntity(request, owner);
        Task saved = taskRepository.saveAndFlush(task);
        outboxEventService.enqueueCreated(saved);
        return taskMapper.toDto(saved);
    }

    @CacheEvict(value = CacheNames.TASK, key = "#id")
    @Transactional
    public TaskResponse updateTask(Long id, TaskUpdateRequest request) {
        Task task = findTask(id);
        if (task.getVersion() != null && !task.getVersion().equals(request.version())) {
            throw new StaleTaskException(id);
        }
        if (request.dueAt() != null && !request.dueAt().equals(task.getDueAt())
                && request.dueAt().isBefore(clock.instant())) {
            throw new InvalidTaskRequestException("dueAt must not be in the past");
        }
        taskMapper.updateEntity(task, request);
        Task updated = taskRepository.saveAndFlush(task);
        outboxEventService.enqueueUpdated(updated);
        return taskMapper.toDto(updated);
    }

    @CacheEvict(value = CacheNames.TASK, key = "#id")
    @Transactional
    public void deleteTask(Long id) {
        taskRepository.delete(findTask(id));
    }

    private Task findTask(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task", id));
    }

    private Pageable stable(Pageable pageable) {
        Sort sort = Sort.unsorted();
        for (Sort.Order order : pageable.getSort()) {
            if (!SORT_FIELDS.contains(order.getProperty())) {
                throw new InvalidTaskRequestException("Unsupported sort field: " + order.getProperty());
            }
            sort = sort.and(Sort.by(order));
        }
        if (sort.isUnsorted()) {
            sort = Sort.by(Sort.Order.asc("dueAt"));
        }
        boolean hasId = sort.stream().anyMatch(order -> order.getProperty().equals("id"));
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                hasId ? sort : sort.and(Sort.by("id")));
    }

    private PageResponse<TaskResponse> page(Page<Task> page) {
        return new PageResponse<>(page.map(taskMapper::toDto));
    }
}
