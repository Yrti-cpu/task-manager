package org.yrti.taskmanager.dto;

import org.yrti.taskmanager.model.enums.Category;
import org.yrti.taskmanager.model.enums.Status;

import java.time.LocalDateTime;

public record TaskDTO(
        Long id,
        String name,
        Status status,
        Category category,
        Long ownerId,
        String ownerUsername,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}