package org.yrti.taskmanager.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.cache.redis", name = "enabled", havingValue = "false")
public class LocalCacheConfig {

    @Bean
    CacheManager localCacheManager() {
        return new ConcurrentMapCacheManager(CacheNames.ALL.toArray(String[]::new));
    }
}
