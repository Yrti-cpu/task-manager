package org.yrti.taskmanager.dto;

import java.time.LocalDateTime;

public record UserDTO(
        Long id,
        String username,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
