package org.yrti.taskmanager.config;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxPropertiesTest {
    @Test
    void validatesLimitsAndTiming() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            OutboxProperties properties = new OutboxProperties();
            assertThat(validator.validate(properties)).isEmpty();
            properties.setBatchSize(0);
            properties.setMaxAttempts(0);
            properties.setLeaseDuration(properties.getSendTimeout());
            assertThat(validator.validate(properties)).hasSize(3);
        }
    }

    @Test
    void preservesBootKafkaSecurityAndProducerSettings() {
        KafkaProperties kafka = new KafkaProperties();
        kafka.getProperties().put("security.protocol", "SASL_SSL");
        kafka.getProperties().put("sasl.mechanism", "PLAIN");
        kafka.getProducer().setAcks("all");
        kafka.getProducer().getProperties().put("delivery.timeout.ms", "15000");
        var factory = new KafkaConfig().taskEventProducerFactory(kafka, JsonMapper.builder().build(), new OutboxProperties());
        assertThat(factory.getConfigurationProperties())
                .containsEntry("security.protocol", "SASL_SSL")
                .containsEntry("sasl.mechanism", "PLAIN")
                .containsEntry("acks", "all")
                .containsEntry("delivery.timeout.ms", "15000")
                .containsEntry("max.block.ms", 10000L);
    }

    @Test
    void rejectsProducerBlockingLongerThanLease() {
        KafkaProperties kafka = new KafkaProperties();
        kafka.getProducer().getProperties().put("max.block.ms", "120000");
        assertThatThrownBy(() -> new KafkaConfig().taskEventProducerFactory(kafka,
                JsonMapper.builder().build(), new OutboxProperties()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("outbox lease");
    }
}
