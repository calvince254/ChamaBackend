package com.calpex_technologies.chama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantDto {
    private Long id;
    private String name;
    private String code;
    private String contactEmail;
    private String contactPhone;
    private int memberCount;
    private String status;
    private String plan;
    private String createdAt;
}
