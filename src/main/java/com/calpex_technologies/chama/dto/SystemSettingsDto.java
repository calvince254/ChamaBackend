package com.calpex_technologies.chama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemSettingsDto {
    
    private Long id;
    
    /**
     * Token expiry time in hours
     */
    private Integer tokenExpiryHours;

    /**
     * Minimum allowed value
     */
    private Integer minTokenExpiryHours;

    /**
     * Maximum allowed value
     */
    private Integer maxTokenExpiryHours;
}
