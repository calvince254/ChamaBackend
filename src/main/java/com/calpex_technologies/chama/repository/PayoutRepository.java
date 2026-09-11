package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.Payout;
import com.calpex_technologies.chama.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface PayoutRepository extends JpaRepository<Payout, Long> {
    
    List<Payout> findByTenantIdOrderByCreatedAtDesc(Long tenantId);
    
    Optional<Payout> findByIdAndTenantId(Long id, Long tenantId);
    
    List<Payout> findByTenantIdAndStatusOrderByCreatedAtDesc(Long tenantId, Payout.PayoutStatus status);
    
    List<Payout> findByTenantIdAndPayoutTypeOrderByCreatedAtDesc(Long tenantId, Payout.PayoutType payoutType);
    
    // Find pending payouts awaiting approval
    @Query("SELECT p FROM Payout p WHERE p.tenant.id = :tenantId AND p.status = 'PENDING' ORDER BY p.initiatedAt ASC")
    List<Payout> findPendingApprovalByTenantId(@Param("tenantId") Long tenantId);
    
    // Sum of completed payouts
    @Query("SELECT COALESCE(SUM(p.payoutAmount), 0) FROM Payout p WHERE p.tenant.id = :tenantId AND p.status = 'COMPLETED'")
    BigDecimal sumCompletedPayoutsByTenantId(@Param("tenantId") Long tenantId);
    
    // Count by status
    @Query("SELECT COUNT(p) FROM Payout p WHERE p.tenant.id = :tenantId AND p.status = :status")
    long countByTenantIdAndStatus(@Param("tenantId") Long tenantId, @Param("status") Payout.PayoutStatus status);
    
    // Check for active payouts (not completed/cancelled)
    @Query("SELECT COUNT(p) > 0 FROM Payout p WHERE p.tenant.id = :tenantId AND p.status IN ('DRAFT', 'PENDING', 'APPROVED')")
    boolean hasActivePayouts(@Param("tenantId") Long tenantId);
    
    @Modifying
    void deleteByTenant(Tenant tenant);
}
