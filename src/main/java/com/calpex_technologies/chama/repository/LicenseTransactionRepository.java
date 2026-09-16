package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.LicenseTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface LicenseTransactionRepository extends JpaRepository<LicenseTransaction, Long> {

    List<LicenseTransaction> findByTenantIdOrderByTransactionDateDesc(Long tenantId);

    List<LicenseTransaction> findByTenantIdAndInvoicedFalseOrderByTransactionDateAsc(Long tenantId);

    /**
     * Find uninvoiced transactions that are not yet linked to any invoice
     */
    List<LicenseTransaction> findByTenantIdAndInvoicedFalseAndInvoiceIsNullOrderByTransactionDateAsc(Long tenantId);

    List<LicenseTransaction> findByInvoiceId(Long invoiceId);

    @Query("SELECT lt FROM LicenseTransaction lt WHERE lt.tenant.id = :tenantId " +
           "AND lt.transactionDate BETWEEN :startDate AND :endDate " +
           "ORDER BY lt.transactionDate ASC")
    List<LicenseTransaction> findByTenantIdAndDateRange(
            @Param("tenantId") Long tenantId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT lt FROM LicenseTransaction lt WHERE lt.tenant.id = :tenantId " +
           "AND lt.invoiced = false " +
           "AND lt.transactionDate BETWEEN :startDate AND :endDate " +
           "ORDER BY lt.transactionDate ASC")
    List<LicenseTransaction> findUninvoicedByTenantIdAndDateRange(
            @Param("tenantId") Long tenantId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT COALESCE(SUM(lt.feeAmount), 0) FROM LicenseTransaction lt " +
           "WHERE lt.tenant.id = :tenantId AND lt.invoiced = false")
    BigDecimal sumUninvoicedFeesByTenantId(@Param("tenantId") Long tenantId);

    /**
     * Sum fees for transactions not yet invoiced AND not linked to any pending invoice
     * This gives the "real-time" amount that hasn't been billed at all
     */
    @Query("SELECT COALESCE(SUM(lt.feeAmount), 0) FROM LicenseTransaction lt " +
           "WHERE lt.tenant.id = :tenantId AND lt.invoiced = false AND lt.invoice IS NULL")
    BigDecimal sumUnbilledFeesByTenantId(@Param("tenantId") Long tenantId);

    @Query("SELECT COALESCE(SUM(lt.feeAmount), 0) FROM LicenseTransaction lt " +
           "WHERE lt.tenant.id = :tenantId " +
           "AND lt.transactionDate BETWEEN :startDate AND :endDate")
    BigDecimal sumFeesByTenantIdAndDateRange(
            @Param("tenantId") Long tenantId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT COALESCE(SUM(lt.originalAmount), 0) FROM LicenseTransaction lt " +
           "WHERE lt.tenant.id = :tenantId " +
           "AND lt.transactionType = :type " +
           "AND lt.transactionDate BETWEEN :startDate AND :endDate")
    BigDecimal sumOriginalAmountByTypeAndDateRange(
            @Param("tenantId") Long tenantId,
            @Param("type") LicenseTransaction.TransactionType type,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Check if a transaction has already been recorded (to prevent duplicates)
     */
    boolean existsByTenantIdAndReferenceIdAndReferenceType(Long tenantId, Long referenceId, String referenceType);

    /**
     * Find all uninvoiced transactions for a specific month across all tenants
     */
    @Query("SELECT lt FROM LicenseTransaction lt JOIN FETCH lt.tenant " +
           "WHERE lt.invoiced = false " +
           "AND YEAR(lt.transactionDate) = :year " +
           "AND MONTH(lt.transactionDate) = :month " +
           "ORDER BY lt.tenant.name, lt.transactionDate ASC")
    List<LicenseTransaction> findUninvoicedByMonth(@Param("year") int year, @Param("month") int month);
}
