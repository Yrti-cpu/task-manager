package org.yrti.taskmanager.dto;

import java.time.Instant;

public record UserResponse(Long id, String username, Instant createdAt, Instant updatedAt) {}
