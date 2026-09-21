package com.calpex_technologies.chama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO for the treasurer's payment dashboard.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentDashboardDto {
    
    // Today's statistics
    private long matchedToday;
    private long pendingReview;
    private long unmatchedEvents;
    private BigDecimal totalReceivedToday;
    
    // Devices
    private long activeDevices;
    private List<TreasurerDeviceDto> recentDevices;
    
    // Recent unmatched events (for manual review)
    private List<PaymentEventDto> unmatchedEventsPreview;
    
    // Pending claims awaiting manual verification
    private List<PaymentClaimDto> pendingManualVerification;
}
