package com.otp_system.notification_service.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OtpDeliveryEvent {

    private String phone;
    private String email;
    private long timestamp;
    private String deliveryStatus; // DELIVERED / FAILED
    private String channel;        // SMS / EMAIL
}