package org.yrti.taskmanager.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.yrti.taskmanager.exception.GlobalExceptionHandler;
import org.yrti.taskmanager.service.TaskService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskController.class)
@Import(GlobalExceptionHandler.class)
class TaskControllerOptimisticLockingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TaskService taskService;

    @Test
    void returnsConflictWithoutDatabaseDetailsForConcurrentUpdate() throws Exception {
        // given
        when(taskService.updateTask(eq(11L), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException("SQL UPDATE tasks failed",
                        new IllegalStateException("org.postgresql: private database details")));

        // when
        var result = mockMvc.perform(put("/api/tasks/11")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Updated","status":"COMPLETED","category":"WORK","version":0}
                        """));

        // then
        var response = result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Resource was modified by another request"))
                .andReturn().getResponse();
        assertThat(response.getContentAsString())
                .doesNotContain("SQL", "UPDATE tasks", "org.postgresql", "private database details");
    }
}
