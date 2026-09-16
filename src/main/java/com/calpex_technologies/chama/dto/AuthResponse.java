package com.calpex_technologies.chama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private Long id;
    private String name;
    private String email;
    private String phone;
    private Long tenantId;
    private String tenantName;
    private String roleName;
    private Boolean superAdmin;
    private List<PermissionDto> permissions;  // Legacy - kept for backward compatibility
    private List<String> permissionCodes;     // New granular permission codes
    private String avatarInitials;
    
    // For multi-chama users - if this list is not empty, user needs to select a chama
    private List<TenantSelectionDto> availableTenants;
    private Boolean multiTenant; // True if user needs to select a chama
    
    // For first-login password change requirement
    private Boolean mustChangePassword;
    
    // Session timeout in minutes (0 = no timeout, for frontend idle detection)
    private Integer sessionTimeoutMinutes;
}
