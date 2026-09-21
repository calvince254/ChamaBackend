package com.calpex_technologies.chama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response after registering a treasurer device.
 * Contains the API key that the device will use for authentication.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceRegistrationResponse {
    private Long deviceId;
    private String deviceApiKey;  // Only returned once, on registration
    private String deviceName;
    private String phoneNumber;
    private String message;
}
