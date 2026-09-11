package com.calpex_technologies.chama.dto;

import lombok.*;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FineDto {
    private Long id;
    private Long meetingId;
    private Long memberId;
    private String memberName;
    private String memberNo;
    private Long fineTypeId;
    private String fineTypeName;
    private BigDecimal amount;
    private String status; // UNPAID, PAID, WAIVED
    private String notes;
    private String imposedAt;
    private String imposedByName;
    private String paidAt;
    private BigDecimal paidAmount;
}
