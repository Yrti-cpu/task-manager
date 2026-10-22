package org.yrti.taskmanager.dto;
import org.yrti.taskmanager.model.enums.Category;
import org.yrti.taskmanager.model.enums.Status;
import java.io.Serializable;
import java.time.Instant;
public record TaskResponse(Long id,String name,Status status,Category category,Instant dueAt,Long ownerId,String ownerUsername,Instant createdAt,Instant updatedAt,Long version) implements Serializable {}
