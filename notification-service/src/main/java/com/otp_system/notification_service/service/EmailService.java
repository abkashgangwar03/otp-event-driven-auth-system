package com.otp_system.notification_service.service;


import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @CircuitBreaker(name = "emailService", fallbackMethod = "fallbackEmail")
    @Retry(name = "emailRetry")
    public void sendOtpEmail(String to, String otp) {

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("your-real-gmail@gmail.com");  // IMPORTANT
        message.setTo(to);
        message.setSubject("Your OTP Code");
        message.setText("Your OTP is: " + otp);

        mailSender.send(message);

        log.info("OTP email sent successfully to {}", to);
    }

    public void fallbackEmail(String to, String otp, Throwable ex) {
        log.error("Email sending failed for {}: {}", to, ex.getMessage());
        throw new IllegalStateException("Email service temporarily unavailable");
    }
}
