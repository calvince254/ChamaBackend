package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.LoanPayment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface LoanPaymentRepository extends JpaRepository<LoanPayment, Long> {

    Optional<LoanPayment> findByIdAndTenantId(Long id, Long tenantId);

    // Get all payments for a specific loan
    List<LoanPayment> findByLoanIdOrderByPaymentDateDesc(Long loanId);

    // Get payments by status for a tenant
    List<LoanPayment> findByTenantIdAndStatusOrderByRecordedAtDesc(Long tenantId, LoanPayment.PaymentStatus status);

    // Paginated pending payments for admin
    Page<LoanPayment> findByTenantIdAndStatusOrderByRecordedAtDesc(
            Long tenantId, 
            LoanPayment.PaymentStatus status, 
            Pageable pageable
    );

    // Get all payments for a member's loans
    @Query("SELECT p FROM LoanPayment p WHERE p.member.id = :memberId ORDER BY p.paymentDate DESC")
    List<LoanPayment> findByMemberIdOrderByPaymentDateDesc(@Param("memberId") Long memberId);

    // Get pending payments for a member
    @Query("SELECT p FROM LoanPayment p WHERE p.member.id = :memberId AND p.status = 'PENDING' ORDER BY p.recordedAt DESC")
    List<LoanPayment> findPendingByMemberId(@Param("memberId") Long memberId);

    // Sum of verified payments for a loan
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM LoanPayment p WHERE p.loan.id = :loanId AND p.status = 'VERIFIED'")
    BigDecimal sumVerifiedPaymentsByLoanId(@Param("loanId") Long loanId);

    // Count pending payments for tenant (for badge/notification)
    @Query("SELECT COUNT(p) FROM LoanPayment p WHERE p.tenant.id = :tenantId AND p.status = 'PENDING'")
    long countPendingByTenantId(@Param("tenantId") Long tenantId);
}
