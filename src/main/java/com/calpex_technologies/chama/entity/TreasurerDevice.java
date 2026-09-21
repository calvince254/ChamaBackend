package com.calpex_technologies.chama.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * Registered treasurer device for receiving payment SMS.
 * Each chama can have one or more treasurer devices that capture incoming M-PESA SMS.
 */
@Entity
@Table(name = "treasurer_device", indexes = {
    @Index(name = "idx_treasurer_device_member", columnList = "member_id"),
    @Index(name = "idx_treasurer_device_tenant", columnList = "tenant_id"),
    @Index(name = "idx_treasurer_device_api_key", columnList = "device_api_key", unique = true)
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TreasurerDevice extends TenantAwareEntity {

    /**
     * The member (treasurer) who owns this device.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    /**
     * Unique API key for this device to authenticate SMS uploads.
     */
    @Column(name = "device_api_key", nullable = false, unique = true, length = 64)
    private String deviceApiKey;

    /**
     * Human-readable device name (e.g., "John's Samsung S21").
     */
    @Column(name = "device_name", length = 100)
    private String deviceName;

    /**
     * Phone number registered on this device.
     */
    @Column(name = "phone_number", length = 15)
    private String phoneNumber;

    /**
     * Whether this device is active and should be used for SMS capture.
     */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /**
     * When the device was registered.
     */
    @Column(name = "registered_at", nullable = false)
    private LocalDateTime registeredAt;

    /**
     * Last time this device reported a payment event.
     */
    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    /**
     * FCM token for push notifications to this device.
     */
    @Column(name = "fcm_token", length = 255)
    private String fcmToken;

    /**
     * If device was revoked, who revoked it.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revoked_by_id")
    private Member revokedBy;

    /**
     * When the device was revoked.
     */
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    /**
     * Reason for revocation.
     */
    @Column(name = "revocation_reason", length = 255)
    private String revocationReason;
}
