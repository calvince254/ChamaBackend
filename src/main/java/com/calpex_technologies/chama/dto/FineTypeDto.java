package com.calpex_technologies.chama.dto;

import lombok.*;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FineTypeDto {
    private Long id;
    private String name;
    private String description;
    private BigDecimal amount;
    private boolean active;
    private boolean automatic;
    private String triggerType; // MEETING_ABSENT, LATE_PAYMENT (null if not automatic)
}
