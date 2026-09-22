package com.calpex_technologies.chama.controller;

import com.calpex_technologies.chama.dto.PlatformSettingsDto;
import com.calpex_technologies.chama.security.UserPrincipal;
import com.calpex_technologies.chama.service.PlatformSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/platform")
@RequiredArgsConstructor
public class PlatformSettingsController {
    
    private final PlatformSettingsService settingsService;
    
    /**
     * Public endpoint - anyone can see contact info on login page
     */
    @GetMapping("/contact-info")
    public ResponseEntity<PlatformSettingsDto> getContactInfo() {
        return ResponseEntity.ok(settingsService.getContactInfo());
    }
    
    /**
     * Super Admin only - update contact info
     */
    @PutMapping("/contact-info")
    public ResponseEntity<PlatformSettingsDto> updateContactInfo(
            @RequestBody PlatformSettingsDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        // Extra check in controller - only SUPER_ADMIN can update
        if (!principal.isSuperAdmin()) {
            return ResponseEntity.status(403).build();
        }
        
        return ResponseEntity.ok(settingsService.updateContactInfo(dto, principal.getEmail()));
    }
}
