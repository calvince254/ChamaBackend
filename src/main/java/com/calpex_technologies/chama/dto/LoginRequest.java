package com.calpex_technologies.chama.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
    @NotBlank(message = "Email or phone number is required")
    private String email;  // Can be email or phone number
    
    @NotBlank(message = "Password is required")
    private String password;
}
