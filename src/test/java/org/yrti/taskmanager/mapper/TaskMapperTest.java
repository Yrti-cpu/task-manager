package org.yrti.taskmanager.mapper;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.yrti.taskmanager.dto.TaskCreateRequest;
import org.yrti.taskmanager.dto.TaskUpdateRequest;
import org.yrti.taskmanager.model.Task;
import org.yrti.taskmanager.model.User;
import org.yrti.taskmanager.model.enums.Category;
import org.yrti.taskmanager.model.enums.Status;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class TaskMapperTest {

    private final TaskMapper mapper = Mappers.getMapper(TaskMapper.class);

    @Test
    void mapsCreateRequestWithOwner() {
        // given
        User owner = User.builder().id(7L).username("alice").build();
        Instant dueAt = Instant.parse("2026-10-20T12:00:00Z");
        TaskCreateRequest request = new TaskCreateRequest("Write tests", Status.IN_PROGRESS, Category.WORK, owner.getId(), dueAt);

        // when
        Task task = mapper.toEntity(request, owner);

        // then
        assertEquals("Write tests", task.getName());
        assertEquals(Status.IN_PROGRESS, task.getStatus());
        assertEquals(Category.WORK, task.getCategory());
        assertEquals(dueAt, task.getDueAt());
        assertSame(owner, task.getOwner());
    }

    @Test
    void updatesExistingTaskWithoutReplacingOwner() {
        // given
        User owner = User.builder().id(7L).username("alice").build();
        Task task = Task.builder()
                .id(11L)
                .name("Old name")
                .status(Status.IN_PROGRESS)
                .category(Category.WORK)
                .owner(owner)
                .build();
        Instant dueAt = Instant.parse("2026-10-20T12:00:00Z");
        TaskUpdateRequest request = new TaskUpdateRequest("New name", Status.COMPLETED, Category.EDUCATION, dueAt, 0L);

        // when
        mapper.updateEntity(task, request);

        // then
        assertEquals(11L, task.getId());
        assertEquals("New name", task.getName());
        assertEquals(Status.COMPLETED, task.getStatus());
        assertEquals(Category.EDUCATION, task.getCategory());
        assertEquals(dueAt, task.getDueAt());
        assertSame(owner, task.getOwner());
    }
}
