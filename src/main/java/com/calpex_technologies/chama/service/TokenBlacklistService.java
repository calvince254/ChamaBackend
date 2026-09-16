package com.calpex_technologies.chama.service;

import com.calpex_technologies.chama.entity.BlacklistedToken;
import com.calpex_technologies.chama.repository.BlacklistedTokenRepository;
import com.calpex_technologies.chama.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HexFormat;

/**
 * Service for managing JWT token blacklist.
 * Tokens are blacklisted on logout to prevent reuse.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenBlacklistService {

    private final BlacklistedTokenRepository blacklistedTokenRepository;
    private final JwtUtil jwtUtil;

    /**
     * Blacklist a JWT token (called on logout)
     */
    @Transactional
    public void blacklistToken(String token, Long memberId, String reason) {
        try {
            String tokenHash = hashToken(token);
            
            // Check if already blacklisted
            if (blacklistedTokenRepository.existsByTokenHash(tokenHash)) {
                log.debug("Token already blacklisted for member {}", memberId);
                return;
            }
            
            // Get token expiration from JWT
            Date expiration = jwtUtil.extractExpiration(token);
            LocalDateTime expiresAt = expiration != null 
                ? expiration.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                : LocalDateTime.now().plusHours(24); // Fallback to 24 hours
            
            BlacklistedToken blacklistedToken = BlacklistedToken.builder()
                    .tokenHash(tokenHash)
                    .blacklistedAt(LocalDateTime.now())
                    .expiresAt(expiresAt)
                    .memberId(memberId)
                    .reason(reason)
                    .build();
            
            blacklistedTokenRepository.save(blacklistedToken);
            log.info("Token blacklisted for member {} - reason: {}", memberId, reason);
        } catch (Exception e) {
            log.error("Error blacklisting token: {}", e.getMessage(), e);
        }
    }

    /**
     * Check if a token is blacklisted
     */
    public boolean isTokenBlacklisted(String token) {
        try {
            String tokenHash = hashToken(token);
            return blacklistedTokenRepository.existsByTokenHash(tokenHash);
        } catch (Exception e) {
            log.error("Error checking token blacklist: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Hash the token using SHA-256 (we don't store actual tokens)
     */
    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Cleanup expired blacklisted tokens (run daily at 2 AM)
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupExpiredTokens() {
        int deleted = blacklistedTokenRepository.deleteExpiredTokens(LocalDateTime.now());
        if (deleted > 0) {
            log.info("Cleaned up {} expired blacklisted tokens", deleted);
        }
    }
}
