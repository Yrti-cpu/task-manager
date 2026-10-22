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
        // given
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            OutboxProperties properties = new OutboxProperties();
            // then
            assertThat(validator.validate(properties)).isEmpty();
            // when
            properties.setBatchSize(0);
            properties.setMaxAttempts(0);
            properties.setLeaseDuration(properties.getSendTimeout());
            // then
            assertThat(validator.validate(properties)).hasSize(3);
        }
    }

    @Test
    void preservesBootKafkaSecurityAndProducerSettings() {
        // given
        KafkaProperties kafka = new KafkaProperties();
        kafka.getProperties().put("security.protocol", "SASL_SSL");
        kafka.getProperties().put("sasl.mechanism", "PLAIN");
        kafka.getProducer().setAcks("all");
        kafka.getProducer().getProperties().put("delivery.timeout.ms", "15000");
        // when
        var factory = new KafkaConfig().taskEventProducerFactory(kafka, JsonMapper.builder().build(), new OutboxProperties());
        // then
        assertThat(factory.getConfigurationProperties())
                .containsEntry("security.protocol", "SASL_SSL")
                .containsEntry("sasl.mechanism", "PLAIN")
                .containsEntry("acks", "all")
                .containsEntry("delivery.timeout.ms", "15000")
                .containsEntry("max.block.ms", 10000L);
    }

    @Test
    void rejectsProducerBlockingLongerThanLease() {
        // given
        KafkaProperties kafka = new KafkaProperties();
        kafka.getProducer().getProperties().put("max.block.ms", "120000");
        // when
        assertThatThrownBy(() -> new KafkaConfig().taskEventProducerFactory(kafka,
                JsonMapper.builder().build(), new OutboxProperties()))
                // then
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("outbox lease");
    }
}
