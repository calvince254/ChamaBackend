package com.calpex_technologies.chama.dto;

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
public class ImposeMgrFineRequest {
    
    @NotNull(message = "Cycle ID is required")
    private Long cycleId;
    
    private String reason;
    
    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;
    
    // Either impose on specific members or link to contribution
    private List<Long> memberIds;
    private Long contributionId;
}
