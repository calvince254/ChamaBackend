package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    List<Invoice> findByTenantIdOrderByIssueDateDesc(Long tenantId);

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    Optional<Invoice> findByIdAndTenantId(Long id, Long tenantId);

    @Query("SELECT i FROM Invoice i JOIN FETCH i.tenant WHERE i.id = :id")
    Optional<Invoice> findByIdWithTenant(@Param("id") Long id);

    @Query("SELECT i FROM Invoice i WHERE i.tenant.id = :tenantId AND i.status = :status ORDER BY i.issueDate DESC")
    List<Invoice> findByTenantIdAndStatus(@Param("tenantId") Long tenantId, @Param("status") Invoice.InvoiceStatus status);

    @Query("SELECT i FROM Invoice i WHERE i.status = :status ORDER BY i.dueDate ASC")
    List<Invoice> findByStatus(@Param("status") Invoice.InvoiceStatus status);

    @Query("SELECT i FROM Invoice i WHERE i.status IN ('SENT', 'PARTIAL') AND i.dueDate < :date ORDER BY i.dueDate ASC")
    List<Invoice> findOverdueInvoices(@Param("date") LocalDate date);

    @Query("SELECT i FROM Invoice i WHERE i.tenant.id = :tenantId " +
           "AND i.periodStart <= :date AND i.periodEnd >= :date " +
           "AND i.isAdhoc = false")
    Optional<Invoice> findByTenantIdAndPeriodContaining(@Param("tenantId") Long tenantId, @Param("date") LocalDate date);

    @Query("SELECT COALESCE(SUM(i.balance), 0) FROM Invoice i WHERE i.tenant.id = :tenantId AND i.status IN ('SENT', 'PARTIAL', 'OVERDUE')")
    BigDecimal sumOutstandingBalanceByTenantId(@Param("tenantId") Long tenantId);

    @Query("SELECT i FROM Invoice i JOIN FETCH i.tenant WHERE i.status IN ('SENT', 'PARTIAL', 'OVERDUE') ORDER BY i.dueDate ASC")
    List<Invoice> findAllOutstandingWithTenant();

    @Query("SELECT i FROM Invoice i JOIN FETCH i.tenant WHERE i.status IN ('DRAFT', 'SENT', 'PARTIAL', 'OVERDUE', 'PENDING_CONFIRMATION') ORDER BY i.issueDate DESC")
    List<Invoice> findAllPendingWithTenant();

    @Query("SELECT i FROM Invoice i JOIN FETCH i.tenant WHERE i.status = 'PAID' ORDER BY i.paidDate DESC")
    List<Invoice> findAllPaidWithTenant();

    @Query("SELECT COUNT(i) FROM Invoice i WHERE i.tenant.id = :tenantId AND YEAR(i.issueDate) = :year")
    long countByTenantIdAndYear(@Param("tenantId") Long tenantId, @Param("year") int year);

    /**
     * Check if an invoice exists for the given tenant and billing period
     */
    @Query("SELECT CASE WHEN COUNT(i) > 0 THEN true ELSE false END FROM Invoice i " +
           "WHERE i.tenant.id = :tenantId " +
           "AND i.periodStart = :periodStart " +
           "AND i.periodEnd = :periodEnd " +
           "AND i.isAdhoc = false")
    boolean existsByTenantIdAndPeriod(
            @Param("tenantId") Long tenantId,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd);

    // Paginated queries
    Page<Invoice> findByTenantIdOrderByIssueDateDesc(Long tenantId, Pageable pageable);
    
    @Query("SELECT i FROM Invoice i WHERE i.tenant.id = :tenantId AND i.status = :status ORDER BY i.issueDate DESC")
    Page<Invoice> findByTenantIdAndStatus(@Param("tenantId") Long tenantId, 
                                           @Param("status") Invoice.InvoiceStatus status, 
                                           Pageable pageable);
}
