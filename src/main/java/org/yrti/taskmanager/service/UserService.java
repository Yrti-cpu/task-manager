package org.yrti.taskmanager.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yrti.taskmanager.dao.TaskRepository;
import org.yrti.taskmanager.dto.TaskDTO;
import org.yrti.taskmanager.dto.UserCreateRequest;
import org.yrti.taskmanager.dto.UserDTO;
import org.yrti.taskmanager.dao.UserRepository;
import org.yrti.taskmanager.mapper.TaskMapper;
import org.yrti.taskmanager.mapper.UserMapper;
import org.yrti.taskmanager.exception.DuplicateUsernameException;
import org.yrti.taskmanager.exception.ResourceNotFoundException;
import org.yrti.taskmanager.exception.UserHasTasksException;
import org.yrti.taskmanager.model.User;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final UserMapper userMapper;
    private final TaskMapper taskMapper;

    @Transactional(readOnly = true)
    public List<UserDTO> getAllUsers() {
        log.info("Fetching all users");
        return userRepository.findAll()
                .stream()
                .map(userMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserDTO getUserById(Long id) {
        log.info("Fetching user id={}", id);
        return userMapper.toDto(findUserOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<TaskDTO> getTasksByUserId(Long userId) {
        log.info("Fetching tasks for user id={}", userId);
        findUserOrThrow(userId);
        return taskRepository.findAllByOwnerId(userId)
                .stream()
                .map(taskMapper::toDto)
                .toList();
    }

    @Transactional
    public UserDTO createUser(UserCreateRequest request) {
        log.info("Creating user username={}", request.username());
        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateUsernameException(request.username());
        }
        User user = userMapper.toEntity(request);
        User saved = userRepository.save(user);
        log.info("User created id={}", saved.getId());
        return userMapper.toDto(saved);
    }

    @Transactional
    public void deleteUser(Long id) {
        log.info("Deleting user id={}", id);
        User user = findUserOrThrow(id);
        if (taskRepository.existsByOwnerId(id)) {
            throw new UserHasTasksException(id);
        }
        userRepository.delete(user);
    }

    private User findUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

}