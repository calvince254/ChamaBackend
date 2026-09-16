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
public class TransactionDto {
    private Long id;
    private String date;
    private String type;
    private String description;
    private BigDecimal amount;
    private String direction;
    private String memberName;
    private Long memberId;
    private Long referenceId;
    private String referenceType;
}
