package org.yrti.taskmanager;

import tools.jackson.databind.json.JsonMapper;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "app.cache.redis.enabled=false"
})
@Testcontainers
class TaskManagerApplicationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private JsonMapper objectMapper;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void contextProvidesObjectMapperForOutboxEvents() {
        assertThat(objectMapper).isNotNull();
        java.time.Instant instant = java.time.Instant.parse("2026-10-22T00:00:00Z");
        assertThat(objectMapper.readValue(objectMapper.writeValueAsString(instant), java.time.Instant.class))
                .isEqualTo(instant);
    }

    @Test
    void defaultMigrationsDoNotSeedDemoUsersOrRetainPasswordColumn() {
        assertThat(jdbc.queryForObject("select count(*) from users", Long.class)).isZero();
        assertThat(jdbc.queryForObject("""
                select count(*) from information_schema.columns
                where table_schema = 'public' and table_name = 'users' and column_name = 'password'
                """, Long.class)).isZero();
    }

}
