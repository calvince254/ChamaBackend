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
public class MerryGoRoundFineDto {
    private Long id;
    private Long cycleId;
    private Long memberId;
    private String memberName;
    private Long contributionId;
    private String reason;
    private BigDecimal amount;
    private String imposedDate;
    private String paidDate;
    private String status; // PENDING, PAID, WAIVED
    private Boolean autoImposed;
    private String waivedBy;
    private String waiveReason;
}
