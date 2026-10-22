package org.yrti.taskmanager.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.yrti.taskmanager.dto.TaskResponse;
import org.yrti.taskmanager.dto.PageResponse;
import org.yrti.taskmanager.model.enums.Category;
import org.yrti.taskmanager.model.enums.Status;
import org.yrti.taskmanager.service.TaskService;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TaskControllerTest {

    private final TaskService taskService = mock(TaskService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new TaskController(taskService))
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .build();

    @Test
    void searchesTasksWithPaginationAndFilters() throws Exception {
        // given
        TaskResponse task = new TaskResponse(11L, "Write tests", Status.COMPLETED, Category.WORK,
                Instant.parse("2026-10-20T12:00:00Z"), 7L, "owner", null, null, 0L);
        when(taskService.searchTasks(
                eq(Status.COMPLETED),
                eq(Instant.parse("2026-10-01T00:00:00Z")),
                eq(Instant.parse("2026-10-31T23:59:59Z")),
                any()))
                .thenReturn(new PageResponse<>(List.of(task), 1, 2, 5, 3, false, false));

        // when
        var result = mockMvc.perform(get("/api/tasks/search")
                        .param("status", "COMPLETED")
                        .param("from", "2026-10-01T00:00:00Z")
                        .param("to", "2026-10-31T23:59:59Z")
                        .param("page", "1")
                        .param("size", "2")
                        .param("sort", "dueAt,asc")
                        .contentType(MediaType.APPLICATION_JSON));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(11))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.first").value(false))
                .andExpect(jsonPath("$.last").value(false));

        verify(taskService).searchTasks(
                eq(Status.COMPLETED),
                eq(Instant.parse("2026-10-01T00:00:00Z")),
                eq(Instant.parse("2026-10-31T23:59:59Z")),
                any());
    }

    @Test
    void searchesTasksWithOptionalFiltersOmittedAndDefaultPageable() throws Exception {
        // given
        when(taskService.searchTasks(eq(null), eq(null), eq(null), any()))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0, true, true));

        // when
        var result = mockMvc.perform(get("/api/tasks/search"));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true));

        org.mockito.ArgumentCaptor<org.springframework.data.domain.Pageable> pageable =
                org.mockito.ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(taskService).searchTasks(eq(null), eq(null), eq(null), pageable.capture());
        org.junit.jupiter.api.Assertions.assertEquals(0, pageable.getValue().getPageNumber());
        org.junit.jupiter.api.Assertions.assertEquals(20, pageable.getValue().getPageSize());
        org.junit.jupiter.api.Assertions.assertEquals(
                List.of(),
                pageable.getValue().getSort().stream().map(Object::toString).toList());
    }
}
