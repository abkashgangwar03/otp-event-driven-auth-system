package com.otp_system.auth_service.kafka.producer;

import com.otp_system.auth_service.kafka.event.OtpEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpEventProducer {

    private static final String TOPIC = "auth-events";
    private static final String DLQ_TOPIC = "auth-events-dlq";

    private final KafkaTemplate<String, OtpEvent> kafkaTemplate;

    @Retry(name = "kafkaRetry", fallbackMethod = "publishFallback")
    @CircuitBreaker(name = "kafkaProducerCB", fallbackMethod = "publishFallback")
    public void publish(OtpEvent event) {
        if (event == null) {
            log.warn("Attempted to publish null OtpEvent");
            return;
        }

        // Use phone if present, otherwise email, fallback to "unknown" to avoid null keys
        String key = Optional.ofNullable(event.getPhone())
                .filter(p -> !p.isBlank())
                .orElse(Optional.ofNullable(event.getEmail())
                        .filter(e -> !e.isBlank())
                        .orElse("unknown"));

        kafkaTemplate.send(TOPIC, key, event)
                .orTimeout(3, TimeUnit.SECONDS)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish OTP event for key {}: {}",
                                key, ex.getMessage());
                    } else {
                        log.info("OTP event published successfully for key {} to partition {}",
                                key,
                                result.getRecordMetadata().partition());
                    }
                });
    }

    public void publishFallback(OtpEvent event, Throwable ex) {

        if (event == null) {
            log.error("Fallback triggered but event is null: {}", ex.getMessage());
            return;
        }

        String key = Optional.ofNullable(event.getPhone())
                .filter(p -> !p.isBlank())
                .orElse(Optional.ofNullable(event.getEmail())
                        .filter(e -> !e.isBlank())
                        .orElse("unknown"));

        log.error("Kafka publish failed after retries, sending to DLQ for key {}: {}",
                key, ex.getMessage());

        kafkaTemplate.send(DLQ_TOPIC, key, event);
    }
}