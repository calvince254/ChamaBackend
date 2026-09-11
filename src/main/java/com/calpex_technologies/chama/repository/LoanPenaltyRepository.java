package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.LoanPenalty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface LoanPenaltyRepository extends JpaRepository<LoanPenalty, Long> {
    
    List<LoanPenalty> findByTenantIdOrderByImposedAtDesc(Long tenantId);
    
    List<LoanPenalty> findByLoanIdOrderByImposedAtDesc(Long loanId);
    
    List<LoanPenalty> findByMemberIdOrderByImposedAtDesc(Long memberId);
    
    List<LoanPenalty> findByTenantIdAndStatus(Long tenantId, LoanPenalty.PenaltyStatus status);
    
    Optional<LoanPenalty> findByIdAndTenantId(Long id, Long tenantId);
    
    // Check if penalty already exists for a loan and due date
    boolean existsByLoanIdAndDueDate(Long loanId, LocalDate dueDate);
    
    // Check if penalty already exists for a loan and period
    boolean existsByLoanIdAndPeriodNumber(Long loanId, Integer periodNumber);
    
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM LoanPenalty p WHERE p.tenant.id = :tenantId AND p.status != 'WAIVED'")
    BigDecimal getTotalPenaltiesForTenant(@Param("tenantId") Long tenantId);
    
    @Query("SELECT COALESCE(SUM(p.paidAmount), 0) FROM LoanPenalty p WHERE p.tenant.id = :tenantId")
    BigDecimal getTotalPaidPenaltiesForTenant(@Param("tenantId") Long tenantId);
    
    @Query("SELECT COALESCE(SUM(p.amount - p.paidAmount), 0) FROM LoanPenalty p WHERE p.loan.id = :loanId AND p.status NOT IN ('PAID', 'WAIVED')")
    BigDecimal getTotalUnpaidPenaltiesForLoan(@Param("loanId") Long loanId);
}
