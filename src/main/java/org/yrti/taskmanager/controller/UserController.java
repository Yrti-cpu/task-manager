package org.yrti.taskmanager.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.yrti.taskmanager.dto.*;
import org.yrti.taskmanager.service.TaskService;
import org.yrti.taskmanager.service.UserService;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final TaskService taskService;
    @GetMapping public PageResponse<UserResponse> getAllUsers(@PageableDefault(size = 20) Pageable p) { return userService.getAllUsers(p); }
    @GetMapping("/{id}") public UserResponse getUserById(@PathVariable Long id) { return userService.getUserById(id); }
    @GetMapping("/{id}/tasks") public PageResponse<TaskResponse> getTasks(@PathVariable Long id, Pageable p) { return taskService.getTasksByOwner(id, p); }
    @PostMapping public ResponseEntity<UserResponse> createUser(@Valid @RequestBody UserCreateRequest r) { return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(r)); }
    @DeleteMapping("/{id}") public ResponseEntity<Void> deleteUser(@PathVariable Long id) { userService.deleteUser(id); return ResponseEntity.noContent().build(); }
}
