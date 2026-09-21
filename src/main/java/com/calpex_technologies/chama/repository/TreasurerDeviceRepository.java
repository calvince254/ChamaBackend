package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.TreasurerDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TreasurerDeviceRepository extends JpaRepository<TreasurerDevice, Long> {
    
    /**
     * Find device by API key for authentication.
     */
    Optional<TreasurerDevice> findByDeviceApiKey(String deviceApiKey);
    
    /**
     * Find active device by API key.
     */
    @Query("SELECT d FROM TreasurerDevice d WHERE d.deviceApiKey = :apiKey AND d.active = true")
    Optional<TreasurerDevice> findActiveByDeviceApiKey(@Param("apiKey") String apiKey);
    
    /**
     * Find device by ID and tenant.
     */
    Optional<TreasurerDevice> findByIdAndTenantId(Long id, Long tenantId);
    
    /**
     * Find all devices for a tenant.
     */
    List<TreasurerDevice> findByTenantIdOrderByRegisteredAtDesc(Long tenantId);
    
    /**
     * Find active devices for a tenant.
     */
    @Query("SELECT d FROM TreasurerDevice d WHERE d.tenant.id = :tenantId AND d.active = true " +
           "ORDER BY d.lastSeenAt DESC NULLS LAST")
    List<TreasurerDevice> findActiveByTenantId(@Param("tenantId") Long tenantId);
    
    /**
     * Find devices by member.
     */
    List<TreasurerDevice> findByMemberIdOrderByRegisteredAtDesc(Long memberId);
    
    /**
     * Find active device for a member.
     */
    @Query("SELECT d FROM TreasurerDevice d WHERE d.member.id = :memberId AND d.active = true")
    Optional<TreasurerDevice> findActiveByMemberId(@Param("memberId") Long memberId);
    
    /**
     * Check if member has an active device.
     */
    boolean existsByMemberIdAndActiveTrue(Long memberId);
    
    /**
     * Count active devices for a tenant.
     */
    long countByTenantIdAndActiveTrue(Long tenantId);
}
