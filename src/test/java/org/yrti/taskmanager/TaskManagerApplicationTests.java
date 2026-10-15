package org.yrti.taskmanager;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TaskManagerApplicationTests {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void contextProvidesObjectMapperForOutboxEvents() {
        assertThat(objectMapper).isNotNull();
    }

}
