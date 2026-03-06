package com.otp_system.auth_service.controller;

import com.otp_system.auth_service.dto.OtpSendRequest;
import com.otp_system.auth_service.dto.OtpVerifyRequest;
import com.otp_system.auth_service.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/otp")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/send")
    public ResponseEntity<?> sendOtp(@Valid @RequestBody OtpSendRequest request) {
        authService.sendOtp(request);
        return ResponseEntity.ok("OTP sent successfully");
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        String token = authService.verifyOtp(request);
        return ResponseEntity.ok(token);
    }
}
