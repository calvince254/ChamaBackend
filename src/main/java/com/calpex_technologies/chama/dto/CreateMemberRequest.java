package com.calpex_technologies.chama.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateMemberRequest {
    @NotBlank(message = "Name is required")
    private String name;
    
    @NotBlank(message = "Phone is required")
    private String phone;
    
    @Email(message = "Invalid email format")
    private String email;
    
    private String password;
    
    private Long roleId;
    
    /**
     * Password generation mode:
     * - "default" = use 12345678, no email sent
     * - "random" = generate random password, send email
     * - null/empty = use provided password field (backward compatible)
     */
    private String passwordMode;
    
    /**
     * Whether to send welcome email (only applies when passwordMode is "random")
     */
    private Boolean sendEmail;
}
