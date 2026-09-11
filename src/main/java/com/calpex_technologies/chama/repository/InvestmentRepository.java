package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.Investment;
import com.calpex_technologies.chama.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvestmentRepository extends JpaRepository<Investment, Long> {
    
    List<Investment> findByTenantIdOrderByDateInvestedDesc(Long tenantId);
    
    Optional<Investment> findByIdAndTenantId(Long id, Long tenantId);
    
    @Query("SELECT i FROM Investment i WHERE i.tenant.id = :tenantId AND i.approvalStatus = 'APPROVED'")
    List<Investment> findApprovedByTenantId(Long tenantId);
    
    @Query("SELECT COALESCE(SUM(i.currentValue), 0) FROM Investment i WHERE i.tenant.id = :tenantId AND i.approvalStatus = 'APPROVED'")
    BigDecimal sumCurrentValueByTenantId(Long tenantId);

    @Modifying
    void deleteByTenant(Tenant tenant);
}
