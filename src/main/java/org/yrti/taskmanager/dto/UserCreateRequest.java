package org.yrti.taskmanager.dto;

import jakarta.validation.constraints.NotBlank;

public record UserCreateRequest(
        @NotBlank @jakarta.validation.constraints.Size(max = 255) String username
) {
}
