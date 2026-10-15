package org.yrti.taskmanager.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yrti.taskmanager.exception.ResourceNotFoundException;
import org.yrti.taskmanager.dto.TaskCreateRequest;
import org.yrti.taskmanager.dto.TaskDTO;
import org.yrti.taskmanager.dto.TaskUpdateRequest;
import org.yrti.taskmanager.dto.TaskPageResponse;
import org.yrti.taskmanager.model.Task;
import org.yrti.taskmanager.model.User;
import org.yrti.taskmanager.model.enums.Category;
import org.yrti.taskmanager.model.enums.Status;
import org.yrti.taskmanager.dao.TaskRepository;
import org.yrti.taskmanager.dao.UserRepository;
import org.yrti.taskmanager.mapper.TaskMapper;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final TaskMapper taskMapper;
    private final OutboxEventService outboxEventService;

    @Cacheable("tasks")
    @Transactional(readOnly = true)
    public List<TaskDTO> getAllTasks() {
        log.info("Fetching all tasks");
        return taskRepository.findAll()
                .stream()
                .map(taskMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public TaskPageResponse searchTasks(Status status, Instant from, Instant to, Pageable pageable) {
        log.info("Searching tasks status={}, from={}, to={}, page={}, size={}",
                status, from, to, pageable.getPageNumber(), pageable.getPageSize());

        Pageable stablePageable = withStableSort(pageable);
        Page<TaskDTO> page = taskRepository.search(status, from, to, stablePageable)
                .map(taskMapper::toDto);

        return new TaskPageResponse(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    @Cacheable(value = "task", key = "#id")
    @Transactional(readOnly = true)
    public TaskDTO getTaskById(Long id) {
        log.info("Fetching task id={}", id);
        return taskMapper.toDto(findTaskOrThrow(id));
    }

    @Cacheable(value = "taskByStatus", key = "#status.name()")
    @Transactional(readOnly = true)
    public List<TaskDTO> getTasksByStatus(Status status) {
        log.info("Fetching tasks by status={}", status);
        return taskRepository.findAllByStatus(status)
                .stream()
                .map(taskMapper::toDto)
                .toList();
    }

    @Cacheable(value = "taskByCategory", key = "#category.name()")
    @Transactional(readOnly = true)
    public List<TaskDTO> getTasksByCategory(Category category) {
        log.info("Fetching tasks by category={}", category);
        return taskRepository.findAllByCategory(category)
                .stream()
                .map(taskMapper::toDto)
                .toList();
    }

    @Cacheable(value = "tasksByOwner", key = "#ownerId")
    @Transactional(readOnly = true)
    public List<TaskDTO> getTasksByOwner(Long ownerId) {
        log.info("Fetching tasks by ownerId={}", ownerId);
        return taskRepository.findAllByOwnerId(ownerId)
                .stream()
                .map(taskMapper::toDto)
                .toList();
    }

    @CacheEvict(value = {"tasks", "task", "taskByStatus", "taskByCategory", "tasksByOwner"}, allEntries = true)
    @Transactional
    public TaskDTO createTask(TaskCreateRequest request) {
        log.info("Creating task name={}, ownerId={}", request.name(), request.ownerId());
        User owner = userRepository.findById(request.ownerId())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.ownerId()));

        Task task = taskMapper.toEntity(request, owner);

        Task saved = taskRepository.save(task);
        outboxEventService.enqueueCreated(saved);
        log.info("Task created id={}", saved.getId());
        return taskMapper.toDto(saved);
    }

    @CacheEvict(value = {"tasks", "task", "taskByStatus", "taskByCategory", "tasksByOwner"}, allEntries = true)
    @Transactional
    public TaskDTO updateTask(Long id, TaskUpdateRequest request) {
        log.info("Updating task id={}", id);
        Task task = findTaskOrThrow(id);
        taskMapper.updateEntity(task, request);
        Task updated = taskRepository.save(task);
        outboxEventService.enqueueUpdated(updated);
        return taskMapper.toDto(updated);
    }

    @CacheEvict(value = {"tasks", "task", "taskByStatus", "taskByCategory", "tasksByOwner"}, allEntries = true)
    @Transactional
    public void deleteTask(Long id) {
        log.info("Deleting task id={}", id);
        Task task = findTaskOrThrow(id);
        taskRepository.delete(task);
    }

    private Task findTaskOrThrow(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task", id));
    }

    private Pageable withStableSort(Pageable pageable) {
        return PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(
                        Sort.Order.asc("dueAt"),
                        Sort.Order.asc("createdAt"),
                        Sort.Order.asc("id")));
    }

}