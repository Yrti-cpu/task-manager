package org.yrti.taskmanager.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Validated
@Configuration(proxyBeanMethods = false)
@ConfigurationProperties(prefix = "app.outbox.dispatcher")
public class OutboxProperties {
    @Min(1) @Max(10000)
    private int batchSize = 100;
    @Min(1)
    private int maxAttempts = 10;
    @NotNull
    private Duration leaseDuration = Duration.ofMinutes(2);
    @NotNull
    private Duration sendTimeout = Duration.ofSeconds(10);
    @NotNull
    private Duration initialBackoff = Duration.ofSeconds(30);
    @NotNull
    private Duration maxBackoff = Duration.ofHours(1);
    @NotNull
    private Duration retention = Duration.ofDays(7);

    @AssertTrue(message = "durations must be positive, lease must exceed twice send timeout, and max backoff must cover initial backoff")
    public boolean isTimingValid() {
        return positive(leaseDuration) && positive(sendTimeout) && positive(initialBackoff)
                && positive(maxBackoff) && positive(retention)
                && leaseDuration.dividedBy(2).compareTo(sendTimeout) > 0
                && maxBackoff.compareTo(initialBackoff) >= 0;
    }

    private boolean positive(Duration duration) {
        return duration != null && !duration.isNegative() && !duration.isZero()
                && duration.toMillis() > 0;
    }
}
