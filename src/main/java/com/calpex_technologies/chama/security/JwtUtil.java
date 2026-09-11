package com.calpex_technologies.chama.security;

import com.calpex_technologies.chama.entity.SystemSettings;
import com.calpex_technologies.chama.repository.SystemSettingsRepository;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long defaultExpiration;

    private final SystemSettingsRepository systemSettingsRepository;

    public JwtUtil(SystemSettingsRepository systemSettingsRepository) {
        this.systemSettingsRepository = systemSettingsRepository;
    }

    /**
     * Get token expiry from system settings, fallback to default from properties
     */
    private long getExpiration() {
        try {
            return systemSettingsRepository.findFirst()
                    .map(SystemSettings::getTokenExpiryMillis)
                    .orElse(defaultExpiration);
        } catch (Exception e) {
            // Fallback to default if DB not available (e.g., during startup)
            return defaultExpiration;
        }
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Long extractTenantId(String token) {
        return extractClaim(token, claims -> claims.get("tenantId", Long.class));
    }

    public Long extractMemberId(String token) {
        return extractClaim(token, claims -> claims.get("memberId", Long.class));
    }

    public Boolean extractIsSuperAdmin(String token) {
        return extractClaim(token, claims -> claims.get("isSuperAdmin", Boolean.class));
    }

    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    @SuppressWarnings("unchecked")
    public List<String> extractPermissions(String token) {
        return extractClaim(token, claims -> {
            Object perms = claims.get("permissions");
            if (perms instanceof List) {
                return (List<String>) perms;
            }
            return Collections.emptyList();
        });
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * Generate token with permissions included
     * @param email User email
     * @param memberId Member ID
     * @param tenantId Tenant ID
     * @param role Role name
     * @param isSuperAdmin Is super admin
     * @param permissions List of permission strings like "VIEW_LOANS", "CREATE_MEMBERS", etc.
     * @return JWT token
     */
    public String generateToken(String email, Long memberId, Long tenantId, String role, boolean isSuperAdmin, List<String> permissions) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("memberId", memberId);
        claims.put("tenantId", tenantId);
        claims.put("role", role);
        claims.put("isSuperAdmin", isSuperAdmin);
        claims.put("permissions", permissions != null ? permissions : Collections.emptyList());
        return createToken(claims, email);
    }

    /**
     * Generate token without permissions (backward compatibility)
     */
    public String generateToken(String email, Long memberId, Long tenantId, String role, boolean isSuperAdmin) {
        return generateToken(email, memberId, tenantId, role, isSuperAdmin, Collections.emptyList());
    }

    private String createToken(Map<String, Object> claims, String subject) {
        long expiration = getExpiration();
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    public Boolean validateToken(String token, String email) {
        final String extractedEmail = extractEmail(token);
        return (extractedEmail.equals(email) && !isTokenExpired(token));
    }

    public Boolean validateToken(String token) {
        try {
            extractAllClaims(token);
            return !isTokenExpired(token);
        } catch (JwtException e) {
            return false;
        }
    }
}
