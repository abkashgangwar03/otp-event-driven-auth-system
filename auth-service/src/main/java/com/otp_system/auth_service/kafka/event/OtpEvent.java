package com.otp_system.auth_service.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OtpEvent {

    private String phone;
    private String otp;
    private long timestamp;
    private String status;
    private String email;
}
