package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.Loan;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface LoanRepository extends JpaRepository<Loan, Long> {
    List<Loan> findByTenantIdOrderByCreatedAtDesc(Long tenantId);
    Optional<Loan> findByIdAndTenantId(Long id, Long tenantId);
    List<Loan> findByMemberIdAndTenantId(Long memberId, Long tenantId);
    List<Loan> findByMemberIdOrderByCreatedAtDesc(Long memberId);
    
    @Query("SELECT l FROM Loan l WHERE l.tenant.id = :tenantId AND l.status = 'PENDING'")
    List<Loan> findPendingByTenantId(Long tenantId);
    
    @Query("SELECT l FROM Loan l WHERE l.tenant.id = :tenantId AND l.status IN ('PENDING', 'PENDING_APPROVAL', 'PENDING_GUARANTORS')")
    List<Loan> findAwaitingApprovalByTenantId(@Param("tenantId") Long tenantId);
    
    @Query("SELECT l FROM Loan l WHERE l.tenant.id = :tenantId AND l.status = 'APPROVED'")
    List<Loan> findApprovedByTenantId(@Param("tenantId") Long tenantId);
    
    @Query("SELECT l FROM Loan l WHERE l.tenant.id = :tenantId AND l.status = 'ACTIVE'")
    List<Loan> findActiveByTenantId(Long tenantId);
    
    @Query("SELECT l FROM Loan l WHERE l.tenant.id = :tenantId AND l.status IN ('ACTIVE', 'OVERDUE')")
    List<Loan> findActiveAndOverdueByTenantId(@Param("tenantId") Long tenantId);
    
    @Query("SELECT l FROM Loan l WHERE l.tenant.id = :tenantId AND l.status IN ('OVERDUE', 'DEFAULTED')")
    List<Loan> findOverdueByTenantId(@Param("tenantId") Long tenantId);
    
    // Find loans pending approval at a specific level (for approvers, not guarantors)
    @Query("SELECT l FROM Loan l WHERE l.tenant.id = :tenantId " +
           "AND l.status IN ('PENDING', 'PENDING_APPROVAL') " +
           "AND l.currentApprovalLevel = :level - 1")
    List<Loan> findPendingApprovalAtLevel(@Param("tenantId") Long tenantId, @Param("level") Integer level);
    
    // Find loans pending guarantor approval
    @Query("SELECT l FROM Loan l WHERE l.tenant.id = :tenantId AND l.status = 'PENDING_GUARANTORS'")
    List<Loan> findPendingGuarantorsByTenantId(@Param("tenantId") Long tenantId);
    
    // Find loans where next due date has passed
    @Query("SELECT l FROM Loan l WHERE l.tenant.id = :tenantId " +
           "AND l.status = 'ACTIVE' " +
           "AND l.nextDueDate < :today")
    List<Loan> findOverdueLoans(@Param("tenantId") Long tenantId, @Param("today") LocalDate today);
    
    @Query("SELECT COALESCE(SUM(l.balanceOutstanding), 0) FROM Loan l WHERE l.tenant.id = :tenantId AND l.status IN ('ACTIVE', 'OVERDUE')")
    BigDecimal sumOutstandingByTenantId(Long tenantId);
    
    @Query("SELECT COALESCE(SUM(l.balanceOutstanding), 0) FROM Loan l WHERE l.member.id = :memberId AND l.status IN ('ACTIVE', 'OVERDUE')")
    BigDecimal sumOutstandingByMemberId(@Param("memberId") Long memberId);

    // Dashboard analytics queries
    @Query("SELECT COALESCE(SUM(l.balanceOutstanding), 0) FROM Loan l WHERE l.tenant.id = :tenantId AND l.status IN ('ACTIVE', 'OVERDUE')")
    BigDecimal sumOutstandingBalanceByTenantId(@Param("tenantId") Long tenantId);
    
    @Query("SELECT COUNT(l) FROM Loan l WHERE l.tenant.id = :tenantId AND l.status = 'OVERDUE' OR (l.status = 'ACTIVE' AND l.nextDueDate < :today)")
    int countOverdueByTenantId(@Param("tenantId") Long tenantId, @Param("today") LocalDate today);
    
    @Query("SELECT l FROM Loan l WHERE l.tenant.id = :tenantId AND (l.status = 'OVERDUE' OR (l.status = 'ACTIVE' AND l.nextDueDate < :today)) ORDER BY l.nextDueDate ASC")
    List<Loan> findOverdueByTenantId(@Param("tenantId") Long tenantId, @Param("today") LocalDate today);

    @Modifying
    void deleteByTenant(Tenant tenant);

    @Modifying
    void deleteByMember(Member member);
    
    // Paginated queries
    Page<Loan> findByTenantIdOrderByCreatedAtDesc(Long tenantId, Pageable pageable);
    
    Page<Loan> findByMemberIdOrderByCreatedAtDesc(Long memberId, Pageable pageable);
    
    @Query("SELECT l FROM Loan l WHERE l.tenant.id = :tenantId AND l.status = :status ORDER BY l.createdAt DESC")
    Page<Loan> findByTenantIdAndStatus(@Param("tenantId") Long tenantId, 
                                        @Param("status") Loan.LoanStatus status, 
                                        Pageable pageable);
    
    @Query("SELECT l FROM Loan l WHERE l.tenant.id = :tenantId AND l.status IN ('PENDING', 'PENDING_APPROVAL', 'PENDING_GUARANTORS') ORDER BY l.createdAt DESC")
    Page<Loan> findAwaitingApprovalByTenantId(@Param("tenantId") Long tenantId, Pageable pageable);
}
