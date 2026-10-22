package org.yrti.taskmanager.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.yrti.taskmanager.exception.GlobalExceptionHandler;
import org.yrti.taskmanager.exception.InvalidTaskRequestException;
import org.yrti.taskmanager.service.TaskService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskController.class)
@Import(GlobalExceptionHandler.class)
class TaskControllerSortValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TaskService taskService;

    @Test
    void mapsUnsupportedSortFieldExceptionToBadRequest() throws Exception {
        // given
        String sort = "unknownField,asc";
        when(taskService.searchTasks(isNull(), isNull(), isNull(), any()))
                .thenThrow(new InvalidTaskRequestException("Unsupported sort field: unknownField"));

        // when
        var result = mockMvc.perform(get("/api/tasks/search").param("sort", sort));

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Unsupported sort field: unknownField"));
        verify(taskService).searchTasks(isNull(), isNull(), isNull(), any());
    }

}
