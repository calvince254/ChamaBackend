package com.calpex_technologies.chama.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * System-wide settings that apply globally (not tenant-specific).
 * This is a singleton entity - there should only be one row.
 */
@Entity
@Table(name = "system_settings")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class SystemSettings extends BaseEntity {

    /**
     * Token expiry time in hours (default: 24 hours)
     */
    @Column(name = "token_expiry_hours", nullable = false)
    private Integer tokenExpiryHours = 24;

    /**
     * Minimum token expiry allowed (1 hour)
     */
    public static final int MIN_TOKEN_EXPIRY_HOURS = 1;

    /**
     * Maximum token expiry allowed (168 hours = 7 days)
     */
    public static final int MAX_TOKEN_EXPIRY_HOURS = 168;

    /**
     * Get token expiry in milliseconds
     */
    public long getTokenExpiryMillis() {
        return (long) tokenExpiryHours * 60 * 60 * 1000;
    }
}
