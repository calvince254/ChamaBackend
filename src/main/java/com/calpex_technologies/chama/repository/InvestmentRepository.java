package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.Investment;
import com.calpex_technologies.chama.entity.Tenant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvestmentRepository extends JpaRepository<Investment, Long> {
    
    List<Investment> findByTenantIdOrderByDateInvestedDesc(Long tenantId);
    
    Optional<Investment> findByIdAndTenantId(Long id, Long tenantId);
    
    // Paginated queries
    Page<Investment> findByTenantIdOrderByDateInvestedDesc(Long tenantId, Pageable pageable);
    
    @Query("SELECT i FROM Investment i WHERE i.tenant.id = :tenantId AND i.status = :status ORDER BY i.dateInvested DESC")
    Page<Investment> findByTenantIdAndStatus(@Param("tenantId") Long tenantId, 
                                              @Param("status") Investment.InvestmentStatus status, 
                                              Pageable pageable);
    
    @Query("SELECT i FROM Investment i WHERE i.tenant.id = :tenantId AND i.approvalStatus = :approvalStatus ORDER BY i.dateInvested DESC")
    Page<Investment> findByTenantIdAndApprovalStatus(@Param("tenantId") Long tenantId, 
                                                      @Param("approvalStatus") Investment.ApprovalStatus approvalStatus, 
                                                      Pageable pageable);
    
    @Query("SELECT i FROM Investment i WHERE i.tenant.id = :tenantId AND i.status = :status AND i.approvalStatus = :approvalStatus ORDER BY i.dateInvested DESC")
    Page<Investment> findByTenantIdAndStatusAndApprovalStatus(@Param("tenantId") Long tenantId, 
                                                               @Param("status") Investment.InvestmentStatus status,
                                                               @Param("approvalStatus") Investment.ApprovalStatus approvalStatus, 
                                                               Pageable pageable);
    
    @Query("SELECT i FROM Investment i WHERE i.tenant.id = :tenantId AND i.approvalStatus = 'APPROVED'")
    List<Investment> findApprovedByTenantId(Long tenantId);
    
    @Query("SELECT COALESCE(SUM(i.currentValue), 0) FROM Investment i WHERE i.tenant.id = :tenantId AND i.approvalStatus = 'APPROVED' AND i.status = 'ACTIVE'")
    BigDecimal sumCurrentValueByTenantId(Long tenantId);
    
    @Query("SELECT i FROM Investment i WHERE i.tenant.id = :tenantId AND i.approvalStatus = 'PENDING'")
    List<Investment> findPendingByTenantId(@org.springframework.data.repository.query.Param("tenantId") Long tenantId);

    @Modifying
    void deleteByTenant(Tenant tenant);
}
