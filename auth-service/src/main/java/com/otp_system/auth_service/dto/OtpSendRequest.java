package com.otp_system.auth_service.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class OtpSendRequest {

    @Pattern(regexp = "^[0-9]{10,15}$", message = "Invalid phone format")
    private String phone;

    @Email(message = "Invalid email format")
    private String email;

    @AssertTrue(message = "Either phone or email must be provided")
    public boolean isValidIdentity() {
        return (phone != null && !phone.isBlank())
                || (email != null && !email.isBlank());
    }
}