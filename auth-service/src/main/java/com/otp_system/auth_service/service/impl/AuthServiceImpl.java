package com.otp_system.auth_service.service.impl;

import com.otp_system.auth_service.Redis.RedisOtpService;
import com.otp_system.auth_service.dto.OtpSendRequest;
import com.otp_system.auth_service.dto.OtpVerifyRequest;
import com.otp_system.auth_service.entity.User;
import com.otp_system.auth_service.kafka.event.OtpEvent;
import com.otp_system.auth_service.kafka.producer.OtpEventProducer;
import com.otp_system.auth_service.repository.UserRepository;
import com.otp_system.auth_service.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RedisOtpService redisOtpService;
    private final PasswordEncoder passwordEncoder;
    private final OtpEventProducer otpEventProducer;
    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private static final int MAX_VERIFY_ATTEMPTS = 5;
    private static final int MAX_SEND_PER_HOUR = 10;
    private static final int MAX_RESENDS = 4;
    private static final long BLOCK_DURATION_MS = 60 * 60 * 1000; // 1 hour

    private String buildOtpKey(String identity) {
        return "otp:" + identity;
    }

    private String buildAttemptKey(String identity) {
        return "attempt:" + identity;
    }

    private String buildSendKey(String identity) {
        return "sendcount:" + identity;
    }

    private String buildResendKey(String identity) {
        return "resend:" + identity;
    }

    private String buildBlockKey(String identity) {
        return "blocked:" + identity;
    }

    private String extractIdentity(String phone, String email) {
        if (phone != null && !phone.isBlank()) {
            return "phone:" + phone;
        }
        if (email != null && !email.isBlank()) {
            return "email:" + email;
        }
        throw new IllegalArgumentException("Phone or Email required");
    }

    @Override
    public void sendOtp(OtpSendRequest request) {

        User user = findOrCreateUser(request);

        String identity = extractIdentity(request.getPhone(), request.getEmail());

        // Check if user is blocked
        String blockKey = buildBlockKey(identity);
        String blocked = redisOtpService.getOtp(blockKey);
        if (blocked != null) {
            throw new IllegalStateException("User temporarily blocked. Try again later.");
        }

        // Enforce hourly OTP send limit
        String sendKey = buildSendKey(identity);
        Long sendCount = redisOtpService.getAttempts(sendKey);
        if (sendCount != null && sendCount >= MAX_SEND_PER_HOUR) {
            redisOtpService.saveOtp(blockKey, "BLOCKED");
            throw new IllegalStateException("Too many OTP requests. User blocked for one hour.");
        }

        String otpKey = buildOtpKey(identity);
        String existingOtp = redisOtpService.getOtp(otpKey);

        boolean isResend = existingOtp != null;

        if (isResend) {
            String resendKey = buildResendKey(identity);
            Long resendCount = redisOtpService.getAttempts(resendKey);

            if (resendCount != null && resendCount >= MAX_RESENDS) {
                throw new IllegalStateException("Maximum resend attempts reached. Please wait for OTP expiry.");
            }

            log.info("Resending OTP for {}", identity);
            redisOtpService.incrementAttempts(resendKey);
            redisOtpService.deleteKey(otpKey);
        } else {
            // Fresh OTP request counts toward hourly limit
            redisOtpService.incrementAttempts(sendKey);
        }

        // Generate 6-digit OTP
        String otp = String.valueOf(java.util.concurrent.ThreadLocalRandom.current()
                .nextInt(100000, 999999));

        // Hash OTP
        String hashedOtp = passwordEncoder.encode(otp);

        // Store OTP in Redis with TTL
        redisOtpService.saveOtp(otpKey, hashedOtp);

        // Reset attempt counter
        redisOtpService.deleteKey(buildAttemptKey(identity));

        otpEventProducer.publish(
                new OtpEvent(
                        request.getPhone(),
                        otp,
                        System.currentTimeMillis(),
                        "OTP_SENT",
                        request.getEmail()
                )
        );

        log.debug("Generated OTP for {}", identity);
    }

    private User findOrCreateUser(OtpSendRequest request) {

        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            return userRepository.findByPhone(request.getPhone())
                    .orElseGet(() -> createUser(request));
        }

        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            return userRepository.findByEmail(request.getEmail())
                    .orElseGet(() -> createUser(request));
        }

        throw new IllegalArgumentException("Phone or Email required");
    }

    private User createUser(OtpSendRequest request) {

        User user = new User();
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());
        user.setStatus("ACTIVE");

        return userRepository.save(user);
    }

    @Override
    public String verifyOtp(OtpVerifyRequest request) {

        String identity = extractIdentity(request.getPhone(), request.getEmail());

        // Check if user is blocked before verification
        String blockKey = buildBlockKey(identity);
        String blocked = redisOtpService.getOtp(blockKey);
        if (blocked != null) {
            throw new IllegalStateException("User temporarily blocked due to too many failed attempts.");
        }

        String otpKey = buildOtpKey(identity);
        String attemptKey = buildAttemptKey(identity);

        // Check attempts
        Long attempts = redisOtpService.getAttempts(attemptKey);
        if (attempts == null) {
            attempts = 0L;
        }

        if (attempts >= MAX_VERIFY_ATTEMPTS) {
            redisOtpService.saveOtp(buildBlockKey(identity), "BLOCKED");
            throw new IllegalStateException("Maximum attempts exceeded");
        }

        // Fetch OTP from Redis
        String storedHashedOtp = redisOtpService.getOtp(otpKey);

        if (storedHashedOtp == null) {
            throw new IllegalStateException("OTP expired or not found");
        }

        // Compare OTP
        if (!passwordEncoder.matches(request.getOtp(), storedHashedOtp)) {
            redisOtpService.incrementAttempts(attemptKey);
            log.warn("Invalid OTP attempt for identity {}", identity);

            otpEventProducer.publish(
                    new OtpEvent(
                            request.getPhone(),
                            request.getOtp(),
                            System.currentTimeMillis(),
                            "OTP_FAILED",
                            request.getEmail()
                    )
            );

            throw new IllegalArgumentException("Invalid OTP");
        }

        log.info("OTP verified successfully for identity {}", identity);
        // Success: delete keys
        redisOtpService.deleteKey(otpKey);
        redisOtpService.deleteKey(attemptKey);

        otpEventProducer.publish(
                new OtpEvent(
                        request.getPhone(),
                        request.getOtp(),
                        System.currentTimeMillis(),
                        "OTP_VERIFIED",
                        request.getEmail()
                )
        );

        return "VERIFIED_SUCCESSFULLY";
    }
    public void redisFallbackSend(OtpSendRequest request, Throwable ex) {
        log.error("Redis failure during sendOtp: {}", ex.getMessage());
        throw new IllegalStateException("Temporary authentication service issue. Please try again.");
    }

}
