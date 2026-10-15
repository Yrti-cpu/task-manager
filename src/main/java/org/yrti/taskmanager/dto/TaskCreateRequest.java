package org.yrti.taskmanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.yrti.taskmanager.model.enums.Category;
import org.yrti.taskmanager.model.enums.Status;

public record TaskCreateRequest(
        @NotBlank String name,
        @NotNull Status status,
        @NotNull Category category,
        @NotNull Long ownerId
) {
}
