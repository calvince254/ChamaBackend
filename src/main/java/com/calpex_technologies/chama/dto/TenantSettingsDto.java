package com.calpex_technologies.chama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for tenant-specific chama settings (General Settings tab).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantSettingsDto {
    
    private Long tenantId;
    private String name;
    private BigDecimal registrationFee;
    private Integer minMembers;
    private Integer maxMembers;
    private Integer signaturesRequired;
}
