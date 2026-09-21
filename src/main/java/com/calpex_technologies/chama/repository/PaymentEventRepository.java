package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.PaymentEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentEventRepository extends JpaRepository<PaymentEvent, Long> {
    
    /**
     * Find event by reference code.
     */
    Optional<PaymentEvent> findByReferenceCode(String referenceCode);
    
    /**
     * Find events by reference code that haven't been matched yet.
     */
    List<PaymentEvent> findByReferenceCodeAndMatchedFalse(String referenceCode);
    
    /**
     * Find unmatched events for a tenant.
     */
    @Query("SELECT e FROM PaymentEvent e WHERE e.tenant.id = :tenantId AND e.matched = false " +
           "ORDER BY e.capturedAt DESC")
    Page<PaymentEvent> findUnmatchedByTenantId(@Param("tenantId") Long tenantId, Pageable pageable);
    
    /**
     * Find unmatched events older than a certain time (for manual review).
     */
    @Query("SELECT e FROM PaymentEvent e WHERE e.tenant.id = :tenantId AND e.matched = false " +
           "AND e.capturedAt < :threshold ORDER BY e.capturedAt DESC")
    List<PaymentEvent> findUnmatchedOlderThan(
            @Param("tenantId") Long tenantId,
            @Param("threshold") LocalDateTime threshold);
    
    /**
     * Find events for fallback matching (by amount and time window).
     */
    @Query("SELECT e FROM PaymentEvent e WHERE e.tenant.id = :tenantId " +
           "AND e.matched = false " +
           "AND e.amount = :amount " +
           "AND e.capturedAt BETWEEN :startTime AND :endTime")
    List<PaymentEvent> findByAmountAndTimeWindow(
            @Param("tenantId") Long tenantId,
            @Param("amount") BigDecimal amount,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);
    
    /**
     * Find events for fallback matching (by payer number and amount).
     */
    @Query("SELECT e FROM PaymentEvent e WHERE e.tenant.id = :tenantId " +
           "AND e.matched = false " +
           "AND e.payerNumber = :payerNumber " +
           "AND e.amount = :amount " +
           "AND e.capturedAt BETWEEN :startTime AND :endTime")
    List<PaymentEvent> findByPayerNumberAmountAndTimeWindow(
            @Param("tenantId") Long tenantId,
            @Param("payerNumber") String payerNumber,
            @Param("amount") BigDecimal amount,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);
    
    /**
     * Find events by treasurer device.
     */
    Page<PaymentEvent> findByTreasurerDeviceIdOrderByCapturedAtDesc(Long deviceId, Pageable pageable);
    
    /**
     * Count unmatched events for a tenant.
     */
    long countByTenantIdAndMatchedFalse(Long tenantId);
    
    /**
     * Find events received today for a tenant.
     */
    @Query("SELECT e FROM PaymentEvent e WHERE e.tenant.id = :tenantId " +
           "AND e.capturedAt >= :startOfDay ORDER BY e.capturedAt DESC")
    List<PaymentEvent> findTodayByTenantId(
            @Param("tenantId") Long tenantId,
            @Param("startOfDay") LocalDateTime startOfDay);
    
    /**
     * Count matched events today for a tenant.
     */
    @Query("SELECT COUNT(e) FROM PaymentEvent e WHERE e.tenant.id = :tenantId " +
           "AND e.matched = true AND e.capturedAt >= :startOfDay")
    long countMatchedTodayByTenantId(
            @Param("tenantId") Long tenantId,
            @Param("startOfDay") LocalDateTime startOfDay);
}
