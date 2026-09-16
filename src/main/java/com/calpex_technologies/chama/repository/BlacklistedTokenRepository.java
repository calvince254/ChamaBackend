package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.BlacklistedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface BlacklistedTokenRepository extends JpaRepository<BlacklistedToken, Long> {

    /**
     * Check if a token hash is blacklisted
     */
    boolean existsByTokenHash(String tokenHash);

    /**
     * Delete expired blacklisted tokens (cleanup job)
     */
    @Modifying
    @Query("DELETE FROM BlacklistedToken b WHERE b.expiresAt < :now")
    int deleteExpiredTokens(LocalDateTime now);
}
