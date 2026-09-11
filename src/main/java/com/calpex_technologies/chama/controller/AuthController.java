package com.calpex_technologies.chama.controller;

import com.calpex_technologies.chama.dto.AuthResponse;
import com.calpex_technologies.chama.dto.LoginRequest;
import com.calpex_technologies.chama.dto.SuperAdminLoginRequest;
import com.calpex_technologies.chama.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

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
}
