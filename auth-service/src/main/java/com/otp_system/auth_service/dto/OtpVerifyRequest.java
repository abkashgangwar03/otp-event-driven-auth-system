package com.otp_system.auth_service.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class OtpVerifyRequest {

    @Pattern(regexp = "^[0-9]{10,15}$")
    private String phone;

    @Email
    private String email;

    @Pattern(regexp = "^[0-9]{6}$")
    @NotBlank
    private String otp;

    @AssertTrue(message = "Either phone or email must be provided")
    public boolean isValidIdentity() {
        return (phone != null && !phone.isBlank())
                || (email != null && !email.isBlank());
    }
}