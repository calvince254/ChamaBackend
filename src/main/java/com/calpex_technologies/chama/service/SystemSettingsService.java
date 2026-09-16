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
        
        // Update platform info
        if (dto.getPlatformName() != null && !dto.getPlatformName().isBlank()) {
            settings.setPlatformName(dto.getPlatformName());
        }
        if (dto.getPlatformEmail() != null) {
            settings.setPlatformEmail(dto.getPlatformEmail());
        }
        if (dto.getPlatformPhone() != null) {
            settings.setPlatformPhone(dto.getPlatformPhone());
        }
        
        // Update payment info
        if (dto.getMpesaPaybill() != null) {
            settings.setMpesaPaybill(dto.getMpesaPaybill());
        }
        if (dto.getBankName() != null) {
            settings.setBankName(dto.getBankName());
        }
        if (dto.getBankAccountName() != null) {
            settings.setBankAccountName(dto.getBankAccountName());
        }
        if (dto.getBankAccountNumber() != null) {
            settings.setBankAccountNumber(dto.getBankAccountNumber());
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
                            .platformName("Calpex Chama Manager")
                            .platformEmail("support@calpex.co.ke")
                            .tokenExpiryHours(24)
                            .build();
                    return systemSettingsRepository.save(settings);
                });
    }

    /**
     * Get settings for read-only contexts (e.g., PDF generation).
     * Returns default values if settings don't exist, without trying to create them.
     */
    @Transactional(readOnly = true)
    public SystemSettings getSettingsReadOnly() {
        return systemSettingsRepository.findFirst()
                .orElseGet(() -> {
                    // Return a transient entity with defaults - don't save
                    SystemSettings defaults = new SystemSettings();
                    defaults.setPlatformName("Calpex Chama Manager");
                    defaults.setPlatformEmail("support@calpex.co.ke");
                    defaults.setTokenExpiryHours(24);
                    return defaults;
                });
    }

    private SystemSettingsDto toDto(SystemSettings settings) {
        return SystemSettingsDto.builder()
                .id(settings.getId())
                .platformName(settings.getPlatformName())
                .platformEmail(settings.getPlatformEmail())
                .platformPhone(settings.getPlatformPhone())
                .mpesaPaybill(settings.getMpesaPaybill())
                .bankName(settings.getBankName())
                .bankAccountName(settings.getBankAccountName())
                .bankAccountNumber(settings.getBankAccountNumber())
                .tokenExpiryHours(settings.getTokenExpiryHours())
                .minTokenExpiryHours(SystemSettings.MIN_TOKEN_EXPIRY_HOURS)
                .maxTokenExpiryHours(SystemSettings.MAX_TOKEN_EXPIRY_HOURS)
                .build();
    }
}
