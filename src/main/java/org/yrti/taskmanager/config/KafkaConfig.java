package org.yrti.taskmanager.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.yrti.taskmanager.event.TaskEvent;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(KafkaProperties.class)
public class KafkaConfig {
    @Bean
    public ProducerFactory<String, TaskEvent> taskEventProducerFactory(
            KafkaProperties kafkaProperties, JsonMapper mapper, OutboxProperties outboxProperties) {
        var properties = kafkaProperties.buildProducerProperties();
        // Bound send() itself (metadata/buffer acquisition), in addition to waiting for its future.
        properties.putIfAbsent(ProducerConfig.MAX_BLOCK_MS_CONFIG, outboxProperties.getSendTimeout().toMillis());
        long maxBlockMillis = Long.parseLong(properties.get(ProducerConfig.MAX_BLOCK_MS_CONFIG).toString());
        if (maxBlockMillis < 0 || maxBlockMillis >= outboxProperties.getLeaseDuration().toMillis()
                - outboxProperties.getSendTimeout().toMillis()) {
            throw new IllegalArgumentException("Kafka max.block.ms plus send timeout must be shorter than the outbox lease");
        }
        properties.putIfAbsent(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        properties.put(JacksonJsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaProducerFactory<>(properties, new StringSerializer(), new JacksonJsonSerializer<>(mapper));
    }

    @Bean
    public KafkaTemplate<String, TaskEvent> taskEventKafkaTemplate(
            ProducerFactory<String, TaskEvent> taskEventProducerFactory) {
        return new KafkaTemplate<>(taskEventProducerFactory);
    }
}
