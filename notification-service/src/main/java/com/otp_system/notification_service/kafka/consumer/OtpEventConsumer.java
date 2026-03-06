package com.otp_system.notification_service.kafka.consumer;


import com.otp_system.notification_service.kafka.event.OtpEvent;
import com.otp_system.notification_service.service.EmailService;
import com.otp_system.notification_service.service.SmsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

@Service
@Slf4j
@RequiredArgsConstructor
public class OtpEventConsumer {

    private static final String TOPIC = "auth-events";
    private static final String DLQ_TOPIC = "auth-events-dlt";

    private final SmsService smsService;
    private final EmailService emailService;
    private final KafkaTemplate<String, OtpEvent> kafkaTemplate;

    @KafkaListener(topics = TOPIC, groupId = "notification-group")
    @Retry(name = "notificationRetry", fallbackMethod = "consumeFallback")
    @CircuitBreaker(name = "notificationCB", fallbackMethod = "consumeFallback")
    public void consume(OtpEvent event) {

        log.info("Received event: {}", event);

        if (event == null || !"OTP_SENT".equals(event.getStatus())) {
            log.info("Ignoring event with status {}", event != null ? event.getStatus() : "null event");
            return;
        }

        try {

            // If email is provided → send EMAIL
            if (event.getEmail() != null && !event.getEmail().isBlank()) {
                emailService.sendOtpEmail(event.getEmail(), event.getOtp());
                log.info("OTP sent via EMAIL to {}", event.getEmail());
                return;
            }

            // If phone is provided → send SMS
            if (event.getPhone() != null && !event.getPhone().isBlank()) {
                smsService.sendOtp(event.getPhone(), event.getOtp());
                log.info("OTP sent via SMS to {}", event.getPhone());
                return;
            }

            throw new IllegalStateException("No delivery channel provided (phone/email missing)");

        } catch (Exception ex) {
            log.warn("Delivery attempt failed for event {}. Will retry if attempts remain. Reason: {}", event, ex.getMessage());
            throw ex;
        }
    }

    public void consumeFallback(OtpEvent event, Throwable ex) {

        log.error("Delivery failed after retries for event {}. Sending to DLQ. Reason: {}", event, ex.getMessage());

        String key = (event != null && event.getPhone() != null && !event.getPhone().isBlank())
                ? event.getPhone()
                : (event != null && event.getEmail() != null && !event.getEmail().isBlank() ? event.getEmail() : "unknown");

        kafkaTemplate.send(DLQ_TOPIC, key, event);
    }
}