package com.calpex_technologies.chama.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to register a treasurer device for SMS capture.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterDeviceRequest {
    
    @NotBlank(message = "Device name is required")
    private String deviceName;
    
    @NotBlank(message = "Phone number is required")
    private String phoneNumber;
    
    /**
     * FCM token for push notifications (optional).
     */
    private String fcmToken;
}
