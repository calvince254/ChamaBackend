package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.Contribution;
import com.calpex_technologies.chama.entity.Member;
import com.calpex_technologies.chama.entity.Tenant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    
    // Dashboard analytics queries
    @Query("SELECT COALESCE(SUM(c.amountPaid), 0) FROM Contribution c WHERE c.tenant.id = :tenantId AND c.amountPaid IS NOT NULL")
    BigDecimal sumPaidAmountByTenantId(@Param("tenantId") Long tenantId);
    
    @Query("SELECT COALESCE(SUM(c.amountPaid), 0) FROM Contribution c WHERE c.member.id = :memberId AND c.amountPaid IS NOT NULL")
    BigDecimal sumPaidAmountByMemberId(@Param("memberId") Long memberId);
    
    @Query("SELECT COALESCE(SUM(c.amountPaid), 0) FROM Contribution c WHERE c.tenant.id = :tenantId " +
           "AND c.paidOn BETWEEN :startDate AND :endDate AND c.amountPaid IS NOT NULL")
    BigDecimal sumPaidAmountByTenantIdAndDateRange(
            @Param("tenantId") Long tenantId,
            @Param("startDate") java.time.LocalDate startDate,
            @Param("endDate") java.time.LocalDate endDate);
    
    @Query("SELECT c FROM Contribution c WHERE c.tenant.id = :tenantId AND c.amountPaid IS NOT NULL " +
           "AND c.amountPaid > 0 ORDER BY c.paidOn DESC, c.createdAt DESC")
    List<Contribution> findRecentByTenantId(@Param("tenantId") Long tenantId, org.springframework.data.domain.Pageable pageable);
    
    default List<Contribution> findRecentByTenantId(Long tenantId, int limit) {
        return findRecentByTenantId(tenantId, org.springframework.data.domain.PageRequest.of(0, limit));
    }
    
    // Paginated queries
    Page<Contribution> findByTenantIdOrderByMonthDesc(Long tenantId, Pageable pageable);
    
    Page<Contribution> findByTenantIdAndContributionTypeIdAndMonth(
            Long tenantId, Long contributionTypeId, YearMonth month, Pageable pageable);
    
    @Query("SELECT c FROM Contribution c WHERE c.tenant.id = :tenantId AND c.month = :month ORDER BY c.member.name ASC")
    Page<Contribution> findByTenantIdAndMonth(@Param("tenantId") Long tenantId, 
                                               @Param("month") YearMonth month, 
                                               Pageable pageable);
    
    @Query("SELECT c FROM Contribution c WHERE c.tenant.id = :tenantId AND c.contributionType.id = :contributionTypeId " +
           "AND c.month = :month AND c.status = :status ORDER BY c.member.name ASC")
    Page<Contribution> findByTenantIdAndContributionTypeIdAndMonthAndStatus(
            @Param("tenantId") Long tenantId,
            @Param("contributionTypeId") Long contributionTypeId,
            @Param("month") YearMonth month,
            @Param("status") Contribution.ContributionStatus status,
            Pageable pageable);
    
    @Modifying
    @Query("DELETE FROM Contribution c WHERE c.member.id = :memberId")
    void deleteByMemberId(@Param("memberId") Long memberId);
    
    // For welfare payment from contribution fund
    List<Contribution> findByMemberIdAndContributionTypeIdAndTenantId(Long memberId, Long contributionTypeId, Long tenantId);
}
