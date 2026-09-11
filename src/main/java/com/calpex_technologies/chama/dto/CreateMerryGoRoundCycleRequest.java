package com.calpex_technologies.chama.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMerryGoRoundCycleRequest {
    
    @NotBlank(message = "Cycle name is required")
    private String name;
    
    @NotNull(message = "Contribution amount is required")
    @Positive(message = "Contribution amount must be positive")
    private BigDecimal contributionAmount;
    
    @NotBlank(message = "Start date is required")
    private String startDate;
    
    // Cycle type settings
    private Boolean isRecurring;      // true = repeats indefinitely
    private Integer numberOfCycles;   // number of times to run through all participants (only if not recurring)
    
    // Frequency settings
    @NotBlank(message = "Frequency is required")
    private String frequency; // DAILY, WEEKLY, MONTHLY
    
    private Integer dayOfWeek; // 1-7 for weekly (1=Monday, 7=Sunday)
    private Integer dayOfMonth; // 1-31 for specific day
    private Integer weekOfMonth; // 1-4 or -1 for last week
    private Integer dayOfWeekInMonth; // 1-7 for day within that week
    
    // Fine settings
    private BigDecimal latePaymentFineAmount;
    private Boolean autoImposeFine;
    private Integer gracePeriodDays;
    
    // Participant selection: "ALL" or list of member IDs
    private String participantSelection; // "ALL" or "SPECIFIC"
    private List<Long> participantIds;
}
