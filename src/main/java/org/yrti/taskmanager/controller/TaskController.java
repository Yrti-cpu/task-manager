package org.yrti.taskmanager.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.yrti.taskmanager.dto.PageResponse;
import org.yrti.taskmanager.dto.TaskResponse;
import org.yrti.taskmanager.dto.TaskCreateRequest;
import org.yrti.taskmanager.dto.TaskUpdateRequest;
import org.yrti.taskmanager.model.enums.Category;
import org.yrti.taskmanager.model.enums.Status;
import org.yrti.taskmanager.service.TaskService;
import java.time.Instant;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {
    private final TaskService taskService;

    @GetMapping
    public PageResponse<TaskResponse> getAllTasks(@PageableDefault(size = 20) Pageable pageable) {
        return taskService.getAllTasks(pageable);
    }

    @GetMapping("/search")
    public PageResponse<TaskResponse> searchTasks(@RequestParam(required = false) Status status,
                                        @RequestParam(required = false) Instant from,
                                        @RequestParam(required = false) Instant to,
                                        @PageableDefault(size = 20) Pageable pageable) {
        return taskService.searchTasks(status, from, to, pageable);
    }

    @GetMapping("/{id}") public TaskResponse getTaskById(@PathVariable Long id) { return taskService.getTaskById(id); }
    @GetMapping("/by-status/{status}") public PageResponse<TaskResponse> getByStatus(@PathVariable Status status, Pageable p) { return taskService.getTasksByStatus(status, p); }
    @GetMapping("/by-category/{category}") public PageResponse<TaskResponse> getByCategory(@PathVariable Category category, Pageable p) { return taskService.getTasksByCategory(category, p); }
    @GetMapping("/by-owner/{ownerId}") public PageResponse<TaskResponse> getByOwner(@PathVariable Long ownerId, Pageable p) { return taskService.getTasksByOwner(ownerId, p); }

    @PostMapping
    public ResponseEntity<TaskResponse> createTask(@Valid @RequestBody TaskCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(taskService.createTask(request));
    }

    @PutMapping("/{id}")
    public TaskResponse updateTask(@PathVariable Long id, @Valid @RequestBody TaskUpdateRequest request) { return taskService.updateTask(id, request); }
    @DeleteMapping("/{id}") public ResponseEntity<Void> deleteTask(@PathVariable Long id) { taskService.deleteTask(id); return ResponseEntity.noContent().build(); }
}
