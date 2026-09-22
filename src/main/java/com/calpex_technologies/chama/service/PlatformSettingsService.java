package com.calpex_technologies.chama.service;

import com.calpex_technologies.chama.dto.PlatformSettingsDto;
import com.calpex_technologies.chama.entity.PlatformSettings;
import com.calpex_technologies.chama.repository.PlatformSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformSettingsService {
    
    private final PlatformSettingsRepository settingsRepository;
    
    private static final String CONTACT_PHONE_KEY = "contact_phone";
    private static final String CONTACT_EMAIL_KEY = "contact_email";
    private static final String CONTACT_NAME_KEY = "contact_name";
    
    @Transactional(readOnly = true)
    public PlatformSettingsDto getContactInfo() {
        return PlatformSettingsDto.builder()
                .contactPhone(getSetting(CONTACT_PHONE_KEY, null))
                .contactEmail(getSetting(CONTACT_EMAIL_KEY, null))
                .contactName(getSetting(CONTACT_NAME_KEY, null))
                .build();
    }
    
    @Transactional
    public PlatformSettingsDto updateContactInfo(PlatformSettingsDto dto, String updatedBy) {
        if (dto.getContactPhone() != null) {
            saveSetting(CONTACT_PHONE_KEY, dto.getContactPhone(), "Contact phone for new chama onboarding", updatedBy);
        }
        if (dto.getContactEmail() != null) {
            saveSetting(CONTACT_EMAIL_KEY, dto.getContactEmail(), "Contact email for new chama onboarding", updatedBy);
        }
        if (dto.getContactName() != null) {
            saveSetting(CONTACT_NAME_KEY, dto.getContactName(), "Contact name for new chama onboarding", updatedBy);
        }
        
        log.info("Platform contact info updated by {}", updatedBy);
        return getContactInfo();
    }
    
    private String getSetting(String key, String defaultValue) {
        return settingsRepository.findByKey(key)
                .map(PlatformSettings::getValue)
                .orElse(defaultValue);
    }
    
    private void saveSetting(String key, String value, String description, String updatedBy) {
        PlatformSettings setting = settingsRepository.findByKey(key)
                .orElseGet(() -> PlatformSettings.builder()
                        .key(key)
                        .description(description)
                        .build());
        
        setting.setValue(value);
        setting.setUpdatedBy(updatedBy);
        settingsRepository.save(setting);
    }
}
