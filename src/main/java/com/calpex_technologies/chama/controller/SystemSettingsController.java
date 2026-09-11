package com.calpex_technologies.chama.controller;

import com.calpex_technologies.chama.dto.SystemSettingsDto;
import com.calpex_technologies.chama.service.SystemSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/system-settings")
@RequiredArgsConstructor
public class SystemSettingsController {

    private final SystemSettingsService systemSettingsService;

    /**
     * Get current system settings
     * Only accessible by super admin
     */
    @GetMapping
    public ResponseEntity<SystemSettingsDto> getSettings() {
        return ResponseEntity.ok(systemSettingsService.getSettings());
    }

    /**
     * Update system settings
     * Only accessible by super admin
     */
    @PutMapping
    public ResponseEntity<SystemSettingsDto> updateSettings(@RequestBody SystemSettingsDto dto) {
        return ResponseEntity.ok(systemSettingsService.updateSettings(dto));
    }
}
