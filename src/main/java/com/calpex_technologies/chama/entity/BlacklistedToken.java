package com.calpex_technologies.chama.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Stores blacklisted JWT tokens (e.g., after logout).
 * Tokens in this table are considered invalid and should be rejected.
 */
@Entity
@Table(name = "blacklisted_tokens", indexes = {
    @Index(name = "idx_token_hash", columnList = "tokenHash"),
    @Index(name = "idx_expires_at", columnList = "expiresAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlacklistedToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Hash of the JWT token (we don't store the actual token for security)
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    // When the token was blacklisted
    @Column(name = "blacklisted_at", nullable = false)
    private LocalDateTime blacklistedAt;

    // When the original token expires (for cleanup purposes)
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    // Member ID who owned this token (for audit purposes)
    @Column(name = "member_id")
    private Long memberId;

    // Reason for blacklisting
    @Column(name = "reason", length = 50)
    private String reason;
}
