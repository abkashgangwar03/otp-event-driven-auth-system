package com.otp_system.notification_service.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SmsService {

    @CircuitBreaker(name = "smsService", fallbackMethod = "fallbackSend")
    @Retry(name = "smsRetry")
    public void sendOtp(String phone, String otp) {

        log.info("Calling external SMS provider for phone {}", phone);

        // Simulate external failure
        // Uncomment to test resilience
        // if (true) throw new RuntimeException("SMS provider down");

        log.info("OTP {} successfully sent to phone {}", otp, phone);
    }

    public void fallbackSend(String phone, String otp, Throwable ex) {
        log.error("SMS sending failed for phone {}: {}", phone, ex.getMessage());
        throw new IllegalStateException("SMS service temporarily unavailable");
    }
}