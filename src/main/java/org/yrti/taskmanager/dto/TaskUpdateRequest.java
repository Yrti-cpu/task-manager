package org.yrti.taskmanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.FutureOrPresent;
import org.yrti.taskmanager.model.enums.Category;
import org.yrti.taskmanager.model.enums.Status;

import java.time.Instant;

public record TaskUpdateRequest(
        @NotBlank String name,
        @NotNull Status status,
        @NotNull Category category,
        @FutureOrPresent Instant dueAt
) {
}
