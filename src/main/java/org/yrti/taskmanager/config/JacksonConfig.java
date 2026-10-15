package org.yrti.taskmanager.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

@Configuration(proxyBeanMethods = false)
public class JacksonConfig {

    @Bean
    ObjectMapper objectMapper() {
        return Jackson2ObjectMapperBuilder.json().build();
    }
}
