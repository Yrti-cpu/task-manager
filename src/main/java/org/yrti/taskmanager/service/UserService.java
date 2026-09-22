package org.yrti.taskmanager.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yrti.taskmanager.dto.TaskDTO;
import org.yrti.taskmanager.dto.UserCreateRequest;
import org.yrti.taskmanager.dto.UserDTO;
import org.yrti.taskmanager.model.Task;
import org.yrti.taskmanager.model.User;
import org.yrti.taskmanager.dao.UserRepository;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<UserDTO> getAllUsers() {
        log.info("Fetching all users");
        return userRepository.findAll()
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserDTO getUserById(Long id) {
        log.info("Fetching user id={}", id);
        return toDto(findUserOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<TaskDTO> getTasksByUserId(Long userId) {
        log.info("Fetching tasks for user id={}", userId);
        User user = findUserOrThrow(userId);
        return user.getTasks()
                .stream()
                .map(this::taskToDto)
                .toList();
    }

    @Transactional
    public UserDTO createUser(UserCreateRequest request) {
        log.info("Creating user username={}", request.username());
        User user = User.builder()
                .username(request.username())
                .password(request.password())
                .build();
        User saved = userRepository.save(user);
        log.info("User created id={}", saved.getId());
        return toDto(saved);
    }

    @Transactional
    public void deleteUser(Long id) {
        log.info("Deleting user id={}", id);
        User user = findUserOrThrow(id);
        userRepository.delete(user);
    }

    private User findUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
    }

    private UserDTO toDto(User user) {
        return new UserDTO(
                user.getId(),
                user.getUsername(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    private TaskDTO taskToDto(Task task) {
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