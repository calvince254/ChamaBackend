package com.calpex_technologies.chama.service;

import com.calpex_technologies.chama.dto.DeviceRegistrationResponse;
import com.calpex_technologies.chama.dto.RegisterDeviceRequest;
import com.calpex_technologies.chama.dto.TreasurerDeviceDto;
import com.calpex_technologies.chama.entity.Member;
import com.calpex_technologies.chama.entity.Tenant;
import com.calpex_technologies.chama.entity.TreasurerDevice;
import com.calpex_technologies.chama.repository.MemberRepository;
import com.calpex_technologies.chama.repository.PaymentEventRepository;
import com.calpex_technologies.chama.repository.TenantRepository;
import com.calpex_technologies.chama.repository.TreasurerDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing treasurer devices used for SMS capture.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TreasurerDeviceService {

    private final TreasurerDeviceRepository deviceRepository;
    private final MemberRepository memberRepository;
    private final TenantRepository tenantRepository;
    private final PaymentEventRepository paymentEventRepository;

    private static final SecureRandom secureRandom = new SecureRandom();

    /**
     * Register a new treasurer device.
     * Returns the API key that the device will use for authentication.
     */
    @Transactional
    public DeviceRegistrationResponse registerDevice(Long memberId, Long tenantId, RegisterDeviceRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found: " + memberId));
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found: " + tenantId));

        // Check if member already has an active device
        if (deviceRepository.existsByMemberIdAndActiveTrue(memberId)) {
            // Deactivate existing device
            deviceRepository.findActiveByMemberId(memberId)
                    .ifPresent(existing -> {
                        existing.setActive(false);
                        deviceRepository.save(existing);
                        log.info("Deactivated existing device for member {}", memberId);
                    });
        }

        // Generate unique API key
        String apiKey = generateApiKey();

        TreasurerDevice device = TreasurerDevice.builder()
                .tenant(tenant)
                .member(member)
                .deviceApiKey(apiKey)
                .deviceName(request.getDeviceName())
                .phoneNumber(normalizePhoneNumber(request.getPhoneNumber()))
                .active(true)
                .registeredAt(LocalDateTime.now())
                .fcmToken(request.getFcmToken())
                .build();

        TreasurerDevice saved = deviceRepository.save(device);
        log.info("Registered treasurer device {} for member {} in tenant {}", 
                saved.getId(), memberId, tenantId);

        return DeviceRegistrationResponse.builder()
                .deviceId(saved.getId())
                .deviceApiKey(apiKey)  // Only returned once!
                .deviceName(saved.getDeviceName())
                .phoneNumber(saved.getPhoneNumber())
                .message("Device registered successfully. Save the API key securely - it will not be shown again.")
                .build();
    }

    /**
     * Get all devices for a tenant.
     */
    @Transactional(readOnly = true)
    public List<TreasurerDeviceDto> getDevicesForTenant(Long tenantId) {
        return deviceRepository.findByTenantIdOrderByRegisteredAtDesc(tenantId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Get active devices for a tenant.
     */
    @Transactional(readOnly = true)
    public List<TreasurerDeviceDto> getActiveDevicesForTenant(Long tenantId) {
        return deviceRepository.findActiveByTenantId(tenantId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Revoke a treasurer device.
     */
    @Transactional
    public void revokeDevice(Long deviceId, Long tenantId, Long revokedById, String reason) {
        TreasurerDevice device = deviceRepository.findByIdAndTenantId(deviceId, tenantId)
                .orElseThrow(() -> new RuntimeException("Device not found: " + deviceId));

        if (!device.isActive()) {
            throw new RuntimeException("Device is already inactive");
        }

        Member revokedBy = memberRepository.findById(revokedById)
                .orElseThrow(() -> new RuntimeException("Member not found: " + revokedById));

        device.setActive(false);
        device.setRevokedBy(revokedBy);
        device.setRevokedAt(LocalDateTime.now());
        device.setRevocationReason(reason);

        deviceRepository.save(device);
        log.info("Device {} revoked by member {} - reason: {}", deviceId, revokedById, reason);
    }

    /**
     * Update device FCM token (for push notifications).
     */
    @Transactional
    public void updateFcmToken(String deviceApiKey, String fcmToken) {
        TreasurerDevice device = deviceRepository.findActiveByDeviceApiKey(deviceApiKey)
                .orElseThrow(() -> new RuntimeException("Invalid device API key"));

        device.setFcmToken(fcmToken);
        deviceRepository.save(device);
    }

    /**
     * Validate device API key and return device.
     */
    @Transactional(readOnly = true)
    public TreasurerDevice validateDevice(String deviceApiKey) {
        return deviceRepository.findActiveByDeviceApiKey(deviceApiKey)
                .orElseThrow(() -> new RuntimeException("Invalid or inactive device"));
    }

    /**
     * Count active devices for a tenant.
     */
    @Transactional(readOnly = true)
    public long countActiveDevices(Long tenantId) {
        return deviceRepository.countByTenantIdAndActiveTrue(tenantId);
    }

    private String generateApiKey() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String normalizePhoneNumber(String phone) {
        if (phone == null) return null;
        phone = phone.replaceAll("[\\s\\-()]+", "");
        if (phone.startsWith("+")) phone = phone.substring(1);
        if (phone.startsWith("0")) phone = "254" + phone.substring(1);
        if (phone.length() == 9) phone = "254" + phone;
        return phone;
    }

    private TreasurerDeviceDto toDto(TreasurerDevice device) {
        LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
        long eventsToday = paymentEventRepository.countMatchedTodayByTenantId(
                device.getTenant().getId(), startOfDay);

        return TreasurerDeviceDto.builder()
                .id(device.getId())
                .memberId(device.getMember().getId())
                .memberName(device.getMember().getName())
                .deviceName(device.getDeviceName())
                .phoneNumber(device.getPhoneNumber())
                .active(device.isActive())
                .registeredAt(device.getRegisteredAt())
                .lastSeenAt(device.getLastSeenAt())
                .revokedById(device.getRevokedBy() != null ? device.getRevokedBy().getId() : null)
                .revokedByName(device.getRevokedBy() != null ? device.getRevokedBy().getName() : null)
                .revokedAt(device.getRevokedAt())
                .revocationReason(device.getRevocationReason())
                .eventsToday(eventsToday)
                .build();
    }
}
