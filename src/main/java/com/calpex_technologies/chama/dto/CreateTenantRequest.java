package com.calpex_technologies.chama.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTenantRequest {
    @NotBlank(message = "Chama name is required")
    private String name;
    
    private String code;  // Optional, will be generated from name if not provided
    
    @NotBlank(message = "Contact email is required")
    @Email(message = "Invalid email format")
    private String contactEmail;
    
    private String contactPhone;
    
    // Admin details (first member)
    @NotBlank(message = "Admin name is required")
    private String adminName;
    
    @NotBlank(message = "Admin email is required")
    @Email(message = "Invalid admin email format")
    private String adminEmail;
    
    private String adminPhone;
    
    @NotBlank(message = "Admin password is required")
    private String adminPassword;
    
    // Role name for the admin (Admin, Treasurer, Member) - defaults to Admin
    private String adminRoleName;
}
