package com.calpex_technologies.chama.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordRequest {
    
    @NotNull(message = "Member ID is required")
    private Long memberId;
    
    @NotBlank(message = "New password is required")
    private String newPassword;
}
