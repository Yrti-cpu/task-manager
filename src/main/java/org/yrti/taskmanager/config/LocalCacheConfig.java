package org.yrti.taskmanager.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.cache.redis", name = "enabled", havingValue = "false")
public class LocalCacheConfig {

    @Bean
    CacheManager localCacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(CacheNames.ALL.toArray(String[]::new));
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofMinutes(5)));
        manager.setAllowNullValues(false);
        return new TransactionAwareCacheManagerProxy(manager);
    }
}
