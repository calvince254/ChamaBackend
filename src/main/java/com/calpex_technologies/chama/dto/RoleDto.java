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
public class RoleDto {
    private Long id;
    private String name;
    private boolean isSystemDefault;
    
    /**
     * Legacy permissions (module + action flags)
     */
    private List<PermissionDto> permissions;
    
    /**
     * New granular permission codes (e.g., VIEW_MEMBERS, DISBURSE_LOAN)
     */
    private List<String> permissionCodes;
    
    private int memberCount;
}
