package org.yrti.taskmanager.dto;

public record UserCreateRequest(
        String username,
        String password
) {
}
