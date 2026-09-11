package com.calpex_technologies.chama.service;

import com.calpex_technologies.chama.dto.SystemSettingsDto;
import com.calpex_technologies.chama.entity.SystemSettings;
import com.calpex_technologies.chama.repository.SystemSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SystemSettingsService {

    private final SystemSettingsRepository systemSettingsRepository;

    /**
     * Get current system settings, creating default if none exists
     */
    @Transactional(readOnly = true)
    public SystemSettingsDto getSettings() {
        SystemSettings settings = getOrCreateSettings();
        return toDto(settings);
    }

    /**
     * Update system settings
     */
    @Transactional
    public SystemSettingsDto updateSettings(SystemSettingsDto dto) {
        SystemSettings settings = getOrCreateSettings();
        
        // Validate and set token expiry hours
        if (dto.getTokenExpiryHours() != null) {
            int hours = dto.getTokenExpiryHours();
            if (hours < SystemSettings.MIN_TOKEN_EXPIRY_HOURS) {
                hours = SystemSettings.MIN_TOKEN_EXPIRY_HOURS;
            } else if (hours > SystemSettings.MAX_TOKEN_EXPIRY_HOURS) {
                hours = SystemSettings.MAX_TOKEN_EXPIRY_HOURS;
            }
            settings.setTokenExpiryHours(hours);
        }
        
        settings = systemSettingsRepository.save(settings);
        return toDto(settings);
    }

    /**
     * Get token expiry in milliseconds (used by JwtUtil)
     */
    @Transactional(readOnly = true)
    public long getTokenExpiryMillis() {
        SystemSettings settings = getOrCreateSettings();
        return settings.getTokenExpiryMillis();
    }

    /**
     * Get or create the singleton settings record
     */
    @Transactional
    public SystemSettings getOrCreateSettings() {
        return systemSettingsRepository.findFirst()
                .orElseGet(() -> {
                    SystemSettings settings = SystemSettings.builder()
                            .tokenExpiryHours(24)
                            .build();
                    return systemSettingsRepository.save(settings);
                });
    }

    private SystemSettingsDto toDto(SystemSettings settings) {
        return SystemSettingsDto.builder()
                .id(settings.getId())
                .tokenExpiryHours(settings.getTokenExpiryHours())
                .minTokenExpiryHours(SystemSettings.MIN_TOKEN_EXPIRY_HOURS)
                .maxTokenExpiryHours(SystemSettings.MAX_TOKEN_EXPIRY_HOURS)
                .build();
    }
}
