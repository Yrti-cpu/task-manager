package org.yrti.taskmanager.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yrti.taskmanager.dao.TaskRepository;
import org.yrti.taskmanager.dao.UserRepository;
import org.yrti.taskmanager.dto.PageResponse;
import org.yrti.taskmanager.dto.UserCreateRequest;
import org.yrti.taskmanager.dto.UserResponse;
import org.yrti.taskmanager.exception.DuplicateUsernameException;
import org.yrti.taskmanager.exception.ResourceNotFoundException;
import org.yrti.taskmanager.exception.UserHasTasksException;
import org.yrti.taskmanager.mapper.UserMapper;
import org.yrti.taskmanager.model.User;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.util.Set;
import org.yrti.taskmanager.exception.InvalidTaskRequestException;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> getAllUsers(Pageable pageable) {
        Page<UserResponse> page = userRepository.findAll(stable(pageable)).map(userMapper::toDto);
        return new PageResponse<>(page);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        return userMapper.toDto(userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id)));
    }

    @Transactional
    public UserResponse createUser(UserCreateRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateUsernameException(request.username());
        }
        return userMapper.toDto(userRepository.save(userMapper.toEntity(request)));
    }

    private Pageable stable(Pageable pageable) {
        Sort sort = pageable.getSort().isSorted()
                ? pageable.getSort()
                : Sort.by(Sort.Order.asc("id"));
        for (Sort.Order order : sort) {
            if (!Set.of("id", "username", "createdAt", "updatedAt").contains(order.getProperty())) {
                throw new InvalidTaskRequestException("Unsupported sort field: " + order.getProperty());
            }
        }
        boolean hasId = sort.stream().anyMatch(order -> order.getProperty().equals("id"));
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                hasId ? sort : sort.and(Sort.by("id")));
    }

    @Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        if (taskRepository.existsByOwnerId(id)) {
            throw new UserHasTasksException(id);
        }
        userRepository.delete(user);
    }
}
