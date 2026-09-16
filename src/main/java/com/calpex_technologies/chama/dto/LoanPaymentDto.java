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
public class LoanPaymentDto {
    private Long id;
    private Long loanId;
    private Long memberId;
    private String memberName;
    private String memberNo;
    private String loanProductName;
    private BigDecimal loanPrincipal;
    private BigDecimal loanBalance;
    
    private BigDecimal amount;
    private String paymentDate;
    private String paymentMethod;
    private String transactionReference;
    private String notes;
    private String status;
    
    // Recorded by
    private Long recordedById;
    private String recordedByName;
    private String recordedAt;
    
    // Verification
    private Long verifiedById;
    private String verifiedByName;
    private String verifiedAt;
    private String rejectionReason;
    private String rejectedAt;
}
