package com.otp_system.auth_service.service;



import com.otp_system.auth_service.dto.OtpSendRequest;
import com.otp_system.auth_service.dto.OtpVerifyRequest;

public interface AuthService {

    void sendOtp(OtpSendRequest request);

    String verifyOtp(OtpVerifyRequest request);
}