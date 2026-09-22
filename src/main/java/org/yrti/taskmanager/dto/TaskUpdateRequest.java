package org.yrti.taskmanager.dto;

import org.yrti.taskmanager.model.enums.Category;
import org.yrti.taskmanager.model.enums.Status;

public record TaskUpdateRequest(
        String name,
        Status status,
        Category category
) {
}