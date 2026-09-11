package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.Contribution;
import com.calpex_technologies.chama.entity.Member;
import com.calpex_technologies.chama.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

@Repository
public interface ContributionRepository extends JpaRepository<Contribution, Long> {
    List<Contribution> findByTenantIdOrderByMonthDesc(Long tenantId);
    Optional<Contribution> findByIdAndTenantId(Long id, Long tenantId);
    List<Contribution> findByMemberIdAndTenantId(Long memberId, Long tenantId);
    List<Contribution> findByMemberIdOrderByMonthDesc(Long memberId);
    Optional<Contribution> findByMemberIdAndMonthAndTenantId(Long memberId, YearMonth month, Long tenantId);
    
    @Query("SELECT COALESCE(SUM(c.amountPaid), 0) FROM Contribution c WHERE c.tenant.id = :tenantId")
    BigDecimal sumAmountPaidByTenantId(Long tenantId);
    
    @Query("SELECT c FROM Contribution c WHERE c.tenant.id = :tenantId AND c.status = 'DEFAULTED'")
    List<Contribution> findDefaultedByTenantId(Long tenantId);

    @Modifying
    void deleteByTenant(Tenant tenant);

    @Modifying
    void deleteByMember(Member member);
    
    // Methods for scheduler
    boolean existsByContributionTypeIdAndMemberIdAndMonth(Long contributionTypeId, Long memberId, YearMonth month);
    
    Optional<Contribution> findByContributionTypeIdAndMemberIdAndMonth(
            Long contributionTypeId, Long memberId, YearMonth month);
    
    // Get contributions by type and period
    List<Contribution> findByTenantIdAndContributionTypeIdAndMonth(
            Long tenantId, Long contributionTypeId, YearMonth month);
    
    // Get all contributions for a contribution type
    List<Contribution> findByTenantIdAndContributionTypeIdOrderByMonthDesc(Long tenantId, Long contributionTypeId);
    
    // Find unpaid/overdue contributions
    @Query("SELECT c FROM Contribution c WHERE c.tenant.id = :tenantId AND c.status IN ('PENDING', 'OVERDUE', 'PARTIAL')")
    List<Contribution> findUnpaidByTenantId(@Param("tenantId") Long tenantId);
    
    @Query("SELECT c FROM Contribution c WHERE c.tenant.id = :tenantId AND c.contributionType.id = :contributionTypeId AND c.status = 'OVERDUE'")
    List<Contribution> findOverdueByTenantIdAndContributionTypeId(
            @Param("tenantId") Long tenantId, @Param("contributionTypeId") Long contributionTypeId);
}
