package org.yrti.taskmanager.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.yrti.taskmanager.dto.TaskCreateRequest;
import org.yrti.taskmanager.dto.TaskDTO;
import org.yrti.taskmanager.dto.TaskUpdateRequest;
import org.yrti.taskmanager.model.enums.Status;
import org.yrti.taskmanager.model.enums.Category;
import org.yrti.taskmanager.service.TaskService;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @GetMapping
    public ResponseEntity<List<TaskDTO>> getAllTasks() {
        return ResponseEntity.ok(taskService.getAllTasks());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskDTO> getTaskById(@PathVariable Long id) {
        return ResponseEntity.ok(taskService.getTaskById(id));
    }

    @GetMapping("/by-status/{status}")
    public ResponseEntity<List<TaskDTO>> getTasksByStatus(@PathVariable Status status) {
        return ResponseEntity.ok(taskService.getTasksByStatus(status));
    }

    @GetMapping("/by-category/{category}")
    public ResponseEntity<List<TaskDTO>> getTasksByCategory(@PathVariable Category category) {
        return ResponseEntity.ok(taskService.getTasksByCategory(category));
    }

    @GetMapping("/by-owner/{ownerId}")
    public ResponseEntity<List<TaskDTO>> getTasksByOwner(@PathVariable Long ownerId) {
        return ResponseEntity.ok(taskService.getTasksByOwner(ownerId));
    }

    @PostMapping
    public ResponseEntity<TaskDTO> createTask(@Valid @RequestBody TaskCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(taskService.createTask(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TaskDTO> updateTask(@PathVariable Long id, @Valid @RequestBody TaskUpdateRequest request) {
        return ResponseEntity.ok(taskService.updateTask(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable Long id) {
        taskService.deleteTask(id);
        return ResponseEntity.noContent().build();
    }
}