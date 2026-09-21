package com.calpex_technologies.chama.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request to create a payable (used internally by module services).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePayableRequest {
    
    @NotNull(message = "Payable type is required")
    private String payableType;
    
    @NotNull(message = "Reference ID is required")
    private Long payableRefId;
    
    @NotNull(message = "Member ID is required")
    private Long memberId;
    
    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;
    
    private String recipientNumber;
    private String recipientName;
    private String description;
    private LocalDate dueDate;
}
