package com.calpex_technologies.chama.dto;

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
public class MerryGoRoundCycleDto {
    private Long id;
    private String name;
    private BigDecimal contributionAmount;
    private String startDate;
    private String endDate;
    private String status;
    
    // Cycle type settings
    private Boolean isRecurring;      // true = repeats indefinitely
    private Integer numberOfCycles;   // only used if not recurring
    private Integer currentCycleNumber; // which iteration we're on
    
    // Frequency settings
    private String frequency; // DAILY, WEEKLY, MONTHLY
    private Integer dayOfWeek; // 1-7 for weekly (1=Monday)
    private Integer dayOfMonth; // 1-31 for monthly
    private Integer weekOfMonth; // 1-4 or -1 for last
    private Integer dayOfWeekInMonth; // 1-7 for specific day in that week
    
    // Fine settings
    private BigDecimal latePaymentFineAmount;
    private Boolean autoImposeFine;
    private Integer gracePeriodDays;
    
    // Summary stats
    private int participantCount;
    private int completedRounds;
    private int totalRounds;
    private BigDecimal totalContributed;
    private BigDecimal totalPaidOut;
    private BigDecimal totalFinesCollected;
    private BigDecimal totalFinesPending;
    
    // Current round info
    private MerryGoRoundRoundDto currentRound;
    
    // Nested data (optional, loaded when viewing details)
    private List<MerryGoRoundParticipantDto> participants;
    private List<MerryGoRoundRoundDto> rounds;
}
