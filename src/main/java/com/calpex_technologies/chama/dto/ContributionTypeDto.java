package com.calpex_technologies.chama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContributionTypeDto {
    private Long id;
    private String name;
    private String description;
    private String category; // NORMAL, WELFARE, EMERGENCY, OTHER
    private BigDecimal amount;
    private String frequency; // WEEKLY, MONTHLY, QUARTERLY, YEARLY
    private Integer dueDay;
    private boolean active;
    private boolean lateFineEnabled;
    private BigDecimal lateFineAmount;
    private Integer gracePeriodDays;
}
