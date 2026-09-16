package com.calpex_technologies.chama.controller;

import com.calpex_technologies.chama.dto.AuthResponse;
import com.calpex_technologies.chama.dto.ChangePasswordRequest;
import com.calpex_technologies.chama.dto.LoginRequest;
import com.calpex_technologies.chama.dto.SuperAdminLoginRequest;
import com.calpex_technologies.chama.security.UserPrincipal;
import com.calpex_technologies.chama.service.AuthService;
import com.calpex_technologies.chama.service.TokenBlacklistService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final TokenBlacklistService tokenBlacklistService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * Login to a specific tenant (used when user is in multiple chamas)
     */
    @PostMapping("/login/{tenantId}")
    public ResponseEntity<AuthResponse> loginToTenant(
            @Valid @RequestBody LoginRequest request,
            @PathVariable Long tenantId) {
        return ResponseEntity.ok(authService.loginToTenant(request, tenantId));
    }

    @PostMapping("/super-admin/login")
    public ResponseEntity<AuthResponse> superAdminLogin(@Valid @RequestBody SuperAdminLoginRequest request) {
        return ResponseEntity.ok(authService.superAdminLogin(request));
    }

    /**
     * Logout endpoint - invalidates the current JWT token
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            HttpServletRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            Long memberId = principal != null ? principal.getMemberId() : null;
            tokenBlacklistService.blacklistToken(token, memberId, "LOGOUT");
        }
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    /**
     * Change password endpoint - used for first-login password change or voluntary change
     */
    @PostMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }
}
