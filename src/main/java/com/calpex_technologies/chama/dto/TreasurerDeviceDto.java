package com.calpex_technologies.chama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for TreasurerDevice entity - represents a registered treasurer device for SMS capture.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TreasurerDeviceDto {
    private Long id;
    private Long memberId;
    private String memberName;
    private String deviceName;
    private String phoneNumber;
    private boolean active;
    private LocalDateTime registeredAt;
    private LocalDateTime lastSeenAt;
    
    // For revocation
    private Long revokedById;
    private String revokedByName;
    private LocalDateTime revokedAt;
    private String revocationReason;
    
    // Statistics
    private long eventsToday;
    private long totalEvents;
}
