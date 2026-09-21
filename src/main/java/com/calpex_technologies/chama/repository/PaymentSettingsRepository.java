package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.PaymentSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentSettingsRepository extends JpaRepository<PaymentSettings, Long> {
    
    /**
     * Find payment settings for a tenant.
     */
    Optional<PaymentSettings> findByTenantId(Long tenantId);
    
    /**
     * Check if payment settings exist for a tenant.
     */
    boolean existsByTenantId(Long tenantId);
}
