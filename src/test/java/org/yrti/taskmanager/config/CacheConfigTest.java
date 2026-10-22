package org.yrti.taskmanager.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
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
        // when
        contextRunner
                .withUserConfiguration(RedisConnectionFactoryConfig.class)
                .run(context -> {
                    // then
                    assertThat(context).hasSingleBean(RedisCacheManager.class);
                    assertThat(context).doesNotHaveBean(TransactionAwareCacheManagerProxy.class);
                    assertThat(context.getBean(CacheManager.class).getCacheNames())
                            .containsExactlyInAnyOrderElementsOf(CacheNames.ALL);
                });
    }

    @Test
    void usesLocalCacheManagerWhenRedisIsDisabled() {
        // when
        contextRunner
                .withPropertyValues("app.cache.redis.enabled=false")
                .run(context -> {
                    // then
                    assertThat(context).hasSingleBean(TransactionAwareCacheManagerProxy.class);
                    assertThat(context).doesNotHaveBean(RedisCacheManager.class);
                    assertThat(context.getBean(CacheManager.class).getCacheNames())
                            .containsExactlyInAnyOrderElementsOf(CacheNames.ALL);
                });
    }

    @Test
    void localCacheIsBoundedAndExpiresEntries() {
        // given
        TransactionAwareCacheManagerProxy manager = (TransactionAwareCacheManagerProxy)
                new LocalCacheConfig().localCacheManager();
        // when
        var cache = (com.github.benmanes.caffeine.cache.Cache<?, ?>)
                manager.getCache(CacheNames.TASK).getNativeCache();
        // then
        assertThat(cache.policy().eviction().orElseThrow().getMaximum()).isEqualTo(10_000);
        assertThat(cache.policy().expireAfterWrite().orElseThrow().getExpiresAfter())
                .isEqualTo(java.time.Duration.ofMinutes(5));
    }

    @Test
    void localEvictionWaitsForTransactionCommit() {
        // given
        var cache = new LocalCacheConfig().localCacheManager().getCache(CacheNames.TASK);
        cache.put(1L, "task");
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            // when
            cache.evict(1L);
            // then
            assertThat(cache.get(1L, String.class)).isEqualTo("task");
            // when
            org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
                    .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);
            // then
            assertThat(cache.get(1L)).isNull();
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class RedisConnectionFactoryConfig {

        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            return mock(RedisConnectionFactory.class);
        }
    }
}
