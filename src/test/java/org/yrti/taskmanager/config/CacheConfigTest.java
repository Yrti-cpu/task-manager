package org.yrti.taskmanager.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.cache.RedisCacheManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CacheConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(CacheConfig.class, RedisCacheConfig.class, LocalCacheConfig.class);

    @Test
    void usesRedisCacheManagerByDefault() {
        contextRunner
                .withUserConfiguration(RedisConnectionFactoryConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(RedisCacheManager.class);
                    assertThat(context).doesNotHaveBean(ConcurrentMapCacheManager.class);
                    assertThat(context.getBean(CacheManager.class).getCacheNames())
                            .containsExactlyInAnyOrder("tasks", "task", "taskByStatus", "taskByCategory", "tasksByOwner");
                });
    }

    @Test
    void usesLocalCacheManagerWhenRedisIsDisabled() {
        contextRunner
                .withPropertyValues("app.cache.redis.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(ConcurrentMapCacheManager.class);
                    assertThat(context).doesNotHaveBean(RedisCacheManager.class);
                    assertThat(context.getBean(CacheManager.class).getCacheNames())
                            .containsExactlyInAnyOrder("tasks", "task", "taskByStatus", "taskByCategory", "tasksByOwner");
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class RedisConnectionFactoryConfig {

        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            return mock(RedisConnectionFactory.class);
        }
    }
}
