package com.calpex_technologies.chama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogDto {
    private Long id;
    private Long tenantId;
    private String tenantName;
    private Long memberId;
    private String memberName;
    private String memberEmail;
    private String module;
    private String action;
    private String httpMethod;
    private String endpoint;
    private String entityType;
    private Long entityId;
    private String description;
    private String oldValue;
    private String newValue;
    private String ipAddress;
    private String userAgent;
    private String timestamp;
    private Integer responseStatus;
    private Long executionTimeMs;
    private String status;
    private String errorMessage;
}
