package org.yrti.taskmanager.dto;

import jakarta.validation.constraints.NotBlank;

public record UserCreateRequest(
        @NotBlank String username,
        @NotBlank String password
) {
}
