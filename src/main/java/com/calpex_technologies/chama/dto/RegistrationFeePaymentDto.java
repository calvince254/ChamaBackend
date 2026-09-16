package com.calpex_technologies.chama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistrationFeePaymentDto {
    private Long id;
    private Long memberId;
    private String memberName;
    private String memberNo;
    private BigDecimal amount;
    private String referenceNumber;
    private String status; // PENDING, AWAITING_VERIFICATION, VERIFIED, REJECTED
    private String paidAt;
    private String verifiedAt;
    private String verifiedByName;
    private String rejectionReason;
    private String rejectedAt;
    private String rejectedByName;
}
