package org.yrti.taskmanager.dto;

import org.yrti.taskmanager.model.enums.Category;
import org.yrti.taskmanager.model.enums.Status;

public record TaskCreateRequest(
        String name,
        Status status,
        Category category,
        Long ownerId
) {
}