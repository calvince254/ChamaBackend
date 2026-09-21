package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.PaymentClaim;
import com.calpex_technologies.chama.entity.PaymentClaimStatus;
import com.calpex_technologies.chama.entity.PaymentMethod;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentClaimRepository extends JpaRepository<PaymentClaim, Long> {
    
    /**
     * Find claim by ID with payable loaded.
     */
    @Query("SELECT c FROM PaymentClaim c JOIN FETCH c.payable WHERE c.id = :id")
    Optional<PaymentClaim> findByIdWithPayable(@Param("id") Long id);
    
    /**
     * Find claim by payable ID.
     */
    List<PaymentClaim> findByPayableIdOrderByCreatedAtDesc(Long payableId);
    
    /**
     * Find claim by payable ID and status (for confirming payments).
     */
    @Query("SELECT c FROM PaymentClaim c JOIN FETCH c.payable WHERE c.payable.id = :payableId " +
           "AND c.status IN :statuses ORDER BY c.createdAt DESC")
    Optional<PaymentClaim> findByPayableIdAndStatusIn(
            @Param("payableId") Long payableId,
            @Param("statuses") List<PaymentClaimStatus> statuses);
    
    /**
     * Find active claim for a payable (not expired, not matched, not failed).
     */
    @Query("SELECT c FROM PaymentClaim c WHERE c.payable.id = :payableId " +
           "AND c.status IN ('PENDING_SMS', 'PENDING_MATCH') " +
           "ORDER BY c.createdAt DESC")
    Optional<PaymentClaim> findActiveClaimByPayableId(@Param("payableId") Long payableId);
    
    /**
     * Find claims by reference code for matching.
     */
    List<PaymentClaim> findByReferenceCodeAndStatus(String referenceCode, PaymentClaimStatus status);
    
    /**
     * Find claims pending match with similar amount and time window.
     */
    @Query("SELECT c FROM PaymentClaim c WHERE c.status = 'PENDING_MATCH' " +
           "AND c.amount = :amount " +
           "AND c.capturedAt BETWEEN :startTime AND :endTime")
    List<PaymentClaim> findPendingMatchByAmountAndTimeWindow(
            @Param("amount") java.math.BigDecimal amount,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);
    
    /**
     * Find expired claims that need to be cleaned up.
     */
    @Query("SELECT c FROM PaymentClaim c WHERE c.status IN ('PENDING_SMS', 'PENDING_MATCH') " +
           "AND c.expiresAt < :now")
    List<PaymentClaim> findExpiredClaims(@Param("now") LocalDateTime now);
    
    /**
     * Find claims that need fallback matching.
     */
    @Query("SELECT c FROM PaymentClaim c WHERE c.status = 'PENDING_MATCH' " +
           "AND c.createdAt < :threshold " +
           "AND c.expiresAt > :now")
    List<PaymentClaim> findClaimsForFallbackMatching(
            @Param("threshold") LocalDateTime threshold,
            @Param("now") LocalDateTime now);
    
    /**
     * Find claims for manual review.
     */
    @Query("SELECT c FROM PaymentClaim c JOIN FETCH c.payable p JOIN FETCH p.tenant " +
           "WHERE p.tenant.id = :tenantId AND c.status = 'MANUAL_REVIEW' " +
           "ORDER BY c.createdAt DESC")
    Page<PaymentClaim> findManualReviewByTenantId(@Param("tenantId") Long tenantId, Pageable pageable);
    
    /**
     * Find claims by payer for history.
     */
    Page<PaymentClaim> findByPayerIdOrderByCreatedAtDesc(Long payerId, Pageable pageable);
    
    /**
     * Find by checkout request ID (for STK Push callback).
     */
    Optional<PaymentClaim> findByCheckoutRequestId(String checkoutRequestId);
    
    /**
     * Count pending claims for a tenant.
     */
    @Query("SELECT COUNT(c) FROM PaymentClaim c JOIN c.payable p " +
           "WHERE p.tenant.id = :tenantId AND c.status = :status")
    long countByTenantIdAndStatus(@Param("tenantId") Long tenantId, @Param("status") PaymentClaimStatus status);
    
    /**
     * Update expired claims to EXPIRED status.
     */
    @Modifying
    @Query("UPDATE PaymentClaim c SET c.status = 'EXPIRED' " +
           "WHERE c.status IN ('PENDING_SMS', 'PENDING_MATCH') AND c.expiresAt < :now")
    int expireClaims(@Param("now") LocalDateTime now);
    
    /**
     * Find claims awaiting manual verification for a tenant.
     */
    @Query("SELECT c FROM PaymentClaim c JOIN FETCH c.payable p JOIN FETCH c.payer " +
           "WHERE p.tenant.id = :tenantId " +
           "AND c.paymentMethod = :method " +
           "AND c.status = 'PENDING_MATCH' " +
           "ORDER BY c.createdAt DESC")
    List<PaymentClaim> findAwaitingVerificationByTenantIdAndMethod(
            @Param("tenantId") Long tenantId,
            @Param("method") PaymentMethod method);
}
