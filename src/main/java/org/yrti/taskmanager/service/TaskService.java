package org.yrti.taskmanager.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yrti.taskmanager.dto.TaskCreateRequest;
import org.yrti.taskmanager.dto.TaskDTO;
import org.yrti.taskmanager.dto.TaskUpdateRequest;
import org.yrti.taskmanager.model.Task;
import org.yrti.taskmanager.model.User;
import org.yrti.taskmanager.model.enums.Status;
import org.yrti.taskmanager.dao.TaskRepository;
import org.yrti.taskmanager.dao.UserRepository;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<TaskDTO> getAllTasks() {
        log.info("Fetching all tasks");
        return taskRepository.findAll()
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public TaskDTO getTaskById(Long id) {
        log.info("Fetching task id={}", id);
        return toDto(findTaskOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<TaskDTO> getTasksByStatus(Status status) {
        log.info("Fetching tasks by status={}", status);
        return taskRepository.findAllByStatus(status)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaskDTO> getTasksByOwner(Long ownerId) {
        log.info("Fetching tasks by ownerId={}", ownerId);
        return taskRepository.findAllByOwnerId(ownerId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public TaskDTO createTask(TaskCreateRequest request) {
        log.info("Creating task name={}, ownerId={}", request.name(), request.ownerId());
        User owner = userRepository.findById(request.ownerId())
                .orElseThrow(() -> new RuntimeException("User not found: " + request.ownerId()));

        Task task = Task.builder()
                .name(request.name())
                .status(request.status())
                .category(request.category())
                .owner(owner)
                .build();

        Task saved = taskRepository.save(task);
        log.info("Task created id={}", saved.getId());
        return toDto(saved);
    }

    @Transactional
    public TaskDTO updateTask(Long id, TaskUpdateRequest request) {
        log.info("Updating task id={}", id);
        Task task = findTaskOrThrow(id);
        task.setName(request.name());
        task.setStatus(request.status());
        task.setCategory(request.category());
        Task updated = taskRepository.save(task);
        return toDto(updated);
    }

    @Transactional
    public void deleteTask(Long id) {
        log.info("Deleting task id={}", id);
        Task task = findTaskOrThrow(id);
        taskRepository.delete(task);
    }

    // ---------- helpers ----------

    private Task findTaskOrThrow(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Task not found: " + id));
    }

    private TaskDTO toDto(Task task) {
        User owner = task.getOwner();
        return new TaskDTO(
                task.getId(),
                task.getName(),
                task.getStatus(),
                task.getCategory(),
                owner != null ? owner.getId() : null,
                owner != null ? owner.getUsername() : null,
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }
}