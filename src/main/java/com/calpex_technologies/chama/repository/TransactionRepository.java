package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.Contribution;
import com.calpex_technologies.chama.entity.Tenant;
import com.calpex_technologies.chama.entity.Transaction;
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

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByTenantIdOrderByDateDescIdDesc(Long tenantId);
    
    @Query("SELECT t FROM Transaction t WHERE t.tenant.id = :tenantId AND t.date BETWEEN :startDate AND :endDate ORDER BY t.date DESC")
    List<Transaction> findByTenantIdAndDateBetween(Long tenantId, LocalDate startDate, LocalDate endDate);
    
    @Query("SELECT t FROM Transaction t WHERE t.tenant.id = :tenantId AND t.date >= :startDate ORDER BY t.date DESC")
    List<Transaction> findByTenantIdAndDateGreaterThanEqual(@Param("tenantId") Long tenantId, @Param("startDate") LocalDate startDate);
    
    @Query("SELECT t FROM Transaction t WHERE t.tenant.id = :tenantId AND t.date <= :endDate ORDER BY t.date DESC")
    List<Transaction> findByTenantIdAndDateLessThanEqual(@Param("tenantId") Long tenantId, @Param("endDate") LocalDate endDate);
    
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.tenant.id = :tenantId AND t.direction = 'IN' AND t.date BETWEEN :startDate AND :endDate")
    BigDecimal sumIncomeByTenantIdAndDateBetween(Long tenantId, LocalDate startDate, LocalDate endDate);
    
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.tenant.id = :tenantId AND t.direction = 'OUT' AND t.date BETWEEN :startDate AND :endDate")
    BigDecimal sumExpenseByTenantIdAndDateBetween(Long tenantId, LocalDate startDate, LocalDate endDate);

    @Modifying
    void deleteByTenant(Tenant tenant);
    
    @Query("SELECT t FROM Transaction t WHERE t.member.id = :memberId ORDER BY t.date DESC, t.id DESC")
    List<Transaction> findTop10ByMemberIdOrderByDateDesc(@Param("memberId") Long memberId);
    
    // Paginated queries for member transactions
    @Query("SELECT t FROM Transaction t WHERE t.member.id = :memberId ORDER BY t.date DESC, t.id DESC")
    Page<Transaction> findByMemberIdOrderByDateDesc(@Param("memberId") Long memberId, Pageable pageable);
    
    @Query("SELECT t FROM Transaction t WHERE t.member.id = :memberId AND t.type = :type ORDER BY t.date DESC, t.id DESC")
    Page<Transaction> findByMemberIdAndType(@Param("memberId") Long memberId, 
                                             @Param("type") Transaction.TransactionType type, 
                                             Pageable pageable);
    
    // Paginated queries for tenant transactions (Finance page)
    Page<Transaction> findByTenantIdOrderByDateDescIdDesc(Long tenantId, Pageable pageable);
    
    @Query("SELECT t FROM Transaction t WHERE t.tenant.id = :tenantId AND t.type = :type ORDER BY t.date DESC, t.id DESC")
    Page<Transaction> findByTenantIdAndType(@Param("tenantId") Long tenantId, 
                                             @Param("type") Transaction.TransactionType type, 
                                             Pageable pageable);
    
    @Query("SELECT t FROM Transaction t WHERE t.tenant.id = :tenantId AND t.direction = :direction ORDER BY t.date DESC, t.id DESC")
    Page<Transaction> findByTenantIdAndDirection(@Param("tenantId") Long tenantId, 
                                                  @Param("direction") Transaction.TransactionDirection direction, 
                                                  Pageable pageable);
    
    @Query("SELECT t FROM Transaction t WHERE t.tenant.id = :tenantId AND t.type = :type AND t.direction = :direction ORDER BY t.date DESC, t.id DESC")
    Page<Transaction> findByTenantIdAndTypeAndDirection(@Param("tenantId") Long tenantId, 
                                                         @Param("type") Transaction.TransactionType type,
                                                         @Param("direction") Transaction.TransactionDirection direction, 
                                                         Pageable pageable);
    
    // For share calculation - get all transactions of a specific type
    @Query("SELECT t FROM Transaction t WHERE t.tenant.id = :tenantId AND t.type = :type")
    List<Transaction> findByTenantIdAndType(@Param("tenantId") Long tenantId, 
                                            @Param("type") Transaction.TransactionType type);
    
    // Loan income queries
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.tenant.id = :tenantId AND t.type = 'LOAN_INTEREST'")
    BigDecimal sumLoanInterestByTenantId(@Param("tenantId") Long tenantId);
    
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.tenant.id = :tenantId AND t.type = 'PROCESSING_FEE'")
    BigDecimal sumProcessingFeesByTenantId(@Param("tenantId") Long tenantId);
    
    @Query("SELECT t FROM Transaction t WHERE t.tenant.id = :tenantId AND t.type IN ('LOAN_INTEREST', 'PROCESSING_FEE') ORDER BY t.date DESC, t.id DESC")
    Page<Transaction> findLoanIncomeByTenantId(@Param("tenantId") Long tenantId, Pageable pageable);
    
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.tenant.id = :tenantId AND t.type = 'LOAN_INTEREST' AND t.date BETWEEN :startDate AND :endDate")
    BigDecimal sumLoanInterestByTenantIdAndDateBetween(@Param("tenantId") Long tenantId, 
                                                        @Param("startDate") LocalDate startDate, 
                                                        @Param("endDate") LocalDate endDate);
    
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.tenant.id = :tenantId AND t.type = 'PROCESSING_FEE' AND t.date BETWEEN :startDate AND :endDate")
    BigDecimal sumProcessingFeesByTenantIdAndDateBetween(@Param("tenantId") Long tenantId, 
                                                          @Param("startDate") LocalDate startDate, 
                                                          @Param("endDate") LocalDate endDate);
    
    // Member-specific queries for filtering
    @Query("SELECT t FROM Transaction t WHERE t.tenant.id = :tenantId AND t.member.id = :memberId ORDER BY t.date DESC, t.id DESC")
    Page<Transaction> findByTenantIdAndMemberIdOrderByDateDescIdDesc(@Param("tenantId") Long tenantId,
                                                                      @Param("memberId") Long memberId,
                                                                      Pageable pageable);
    
    @Query("SELECT t FROM Transaction t WHERE t.tenant.id = :tenantId AND t.member.id = :memberId AND t.type = :type ORDER BY t.date DESC, t.id DESC")
    Page<Transaction> findByTenantIdAndMemberIdAndType(@Param("tenantId") Long tenantId,
                                                        @Param("memberId") Long memberId,
                                                        @Param("type") Transaction.TransactionType type,
                                                        Pageable pageable);
    
    @Query("SELECT t FROM Transaction t WHERE t.tenant.id = :tenantId AND t.member.id = :memberId AND t.direction = :direction ORDER BY t.date DESC, t.id DESC")
    Page<Transaction> findByTenantIdAndMemberIdAndDirection(@Param("tenantId") Long tenantId,
                                                             @Param("memberId") Long memberId,
                                                             @Param("direction") Transaction.TransactionDirection direction,
                                                             Pageable pageable);
    
    @Query("SELECT t FROM Transaction t WHERE t.tenant.id = :tenantId AND t.member.id = :memberId AND t.type = :type AND t.direction = :direction ORDER BY t.date DESC, t.id DESC")
    Page<Transaction> findByTenantIdAndMemberIdAndTypeAndDirection(@Param("tenantId") Long tenantId,
                                                                    @Param("memberId") Long memberId,
                                                                    @Param("type") Transaction.TransactionType type,
                                                                    @Param("direction") Transaction.TransactionDirection direction,
                                                                    Pageable pageable);
    
    // Ledger balance queries
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.tenant.id = :tenantId AND t.direction = 'IN'")
    BigDecimal sumTotalCreditsByTenantId(@Param("tenantId") Long tenantId);
    
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.tenant.id = :tenantId AND t.direction = 'OUT'")
    BigDecimal sumTotalDebitsByTenantId(@Param("tenantId") Long tenantId);
    
    @Modifying
    @Query("DELETE FROM Transaction t WHERE t.member.id = :memberId")
    void deleteByMemberId(@Param("memberId") Long memberId);
    
    /**
     * Sum credits (IN) for a member in a specific contribution type.
     * Used to calculate member's total paid into a contribution type.
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.member.id = :memberId " +
           "AND t.contributionType.id = :contributionTypeId " +
           "AND t.tenant.id = :tenantId " +
           "AND t.direction = 'IN'")
    BigDecimal sumCreditsByMemberAndContributionType(
            @Param("memberId") Long memberId,
            @Param("contributionTypeId") Long contributionTypeId,
            @Param("tenantId") Long tenantId);
    
    /**
     * Sum debits (OUT) for a member in a specific contribution type.
     * Used to calculate member's withdrawals/deductions from a contribution type (e.g., welfare payments).
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.member.id = :memberId " +
           "AND t.contributionType.id = :contributionTypeId " +
           "AND t.tenant.id = :tenantId " +
           "AND t.direction = 'OUT'")
    BigDecimal sumDebitsByMemberAndContributionType(
            @Param("memberId") Long memberId,
            @Param("contributionTypeId") Long contributionTypeId,
            @Param("tenantId") Long tenantId);
    
    // ==================== CONTRIBUTION REPORT QUERIES ====================
    
    /**
     * Get all transactions for a specific contribution type with pagination.
     * Returns both credits (contributions paid) and debits (welfare deductions).
     * If contributionTypeId is null, returns all transactions that have a contributionType.
     */
    @Query("SELECT t FROM Transaction t " +
           "WHERE t.tenant.id = :tenantId " +
           "AND t.contributionType IS NOT NULL " +
           "AND (:contributionTypeId IS NULL OR t.contributionType.id = :contributionTypeId) " +
           "AND (:memberId IS NULL OR t.member.id = :memberId) " +
           "AND (:startDate IS NULL OR t.date >= :startDate) " +
           "AND (:endDate IS NULL OR t.date <= :endDate) " +
           "ORDER BY t.date DESC, t.id DESC")
    Page<Transaction> findContributionTransactions(
            @Param("tenantId") Long tenantId,
            @Param("contributionTypeId") Long contributionTypeId,
            @Param("memberId") Long memberId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);
    
    /**
     * Get all transactions for a specific contribution type without pagination.
     * Used for PDF export.
     */
    @Query("SELECT t FROM Transaction t " +
           "WHERE t.tenant.id = :tenantId " +
           "AND t.contributionType IS NOT NULL " +
           "AND (:contributionTypeId IS NULL OR t.contributionType.id = :contributionTypeId) " +
           "AND (:memberId IS NULL OR t.member.id = :memberId) " +
           "AND (:startDate IS NULL OR t.date >= :startDate) " +
           "AND (:endDate IS NULL OR t.date <= :endDate) " +
           "ORDER BY t.date DESC, t.id DESC")
    List<Transaction> findAllContributionTransactions(
            @Param("tenantId") Long tenantId,
            @Param("contributionTypeId") Long contributionTypeId,
            @Param("memberId") Long memberId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
    
    /**
     * Sum credits for contribution type report (with filters).
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.tenant.id = :tenantId " +
           "AND t.contributionType IS NOT NULL " +
           "AND (:contributionTypeId IS NULL OR t.contributionType.id = :contributionTypeId) " +
           "AND t.direction = 'IN' " +
           "AND (:memberId IS NULL OR t.member.id = :memberId) " +
           "AND (:startDate IS NULL OR t.date >= :startDate) " +
           "AND (:endDate IS NULL OR t.date <= :endDate)")
    BigDecimal sumContributionCredits(
            @Param("tenantId") Long tenantId,
            @Param("contributionTypeId") Long contributionTypeId,
            @Param("memberId") Long memberId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
    
    /**
     * Sum debits for contribution type report (with filters).
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.tenant.id = :tenantId " +
           "AND t.contributionType IS NOT NULL " +
           "AND (:contributionTypeId IS NULL OR t.contributionType.id = :contributionTypeId) " +
           "AND t.direction = 'OUT' " +
           "AND (:memberId IS NULL OR t.member.id = :memberId) " +
           "AND (:startDate IS NULL OR t.date >= :startDate) " +
           "AND (:endDate IS NULL OR t.date <= :endDate)")
    BigDecimal sumContributionDebits(
            @Param("tenantId") Long tenantId,
            @Param("contributionTypeId") Long contributionTypeId,
            @Param("memberId") Long memberId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
    
    /**
     * Count transactions for contribution type report.
     */
    @Query("SELECT COUNT(t) FROM Transaction t " +
           "WHERE t.tenant.id = :tenantId " +
           "AND t.contributionType IS NOT NULL " +
           "AND (:contributionTypeId IS NULL OR t.contributionType.id = :contributionTypeId) " +
           "AND (:memberId IS NULL OR t.member.id = :memberId) " +
           "AND (:startDate IS NULL OR t.date >= :startDate) " +
           "AND (:endDate IS NULL OR t.date <= :endDate)")
    Long countContributionTransactions(
            @Param("tenantId") Long tenantId,
            @Param("contributionTypeId") Long contributionTypeId,
            @Param("memberId") Long memberId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
    
    // ==================== CONTRIBUTION REPORT QUERIES WITH STATUS FILTER ====================
    
    /**
     * Get contribution transactions filtered by contribution status with pagination.
     * Joins with Contribution table to filter by status.
     */
    @Query("SELECT t FROM Transaction t " +
           "JOIN Contribution c ON c.id = t.referenceId AND t.referenceType = 'CONTRIBUTION' " +
           "WHERE t.tenant.id = :tenantId " +
           "AND t.contributionType IS NOT NULL " +
           "AND (:contributionTypeId IS NULL OR t.contributionType.id = :contributionTypeId) " +
           "AND (:memberId IS NULL OR t.member.id = :memberId) " +
           "AND (:startDate IS NULL OR t.date >= :startDate) " +
           "AND (:endDate IS NULL OR t.date <= :endDate) " +
           "AND (:status IS NULL OR c.status = :status) " +
           "ORDER BY t.date DESC, t.id DESC")
    Page<Transaction> findContributionTransactionsWithStatus(
            @Param("tenantId") Long tenantId,
            @Param("contributionTypeId") Long contributionTypeId,
            @Param("memberId") Long memberId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("status") Contribution.ContributionStatus status,
            Pageable pageable);
    
    /**
     * Get all contribution transactions filtered by status (for PDF export).
     */
    @Query("SELECT t FROM Transaction t " +
           "JOIN Contribution c ON c.id = t.referenceId AND t.referenceType = 'CONTRIBUTION' " +
           "WHERE t.tenant.id = :tenantId " +
           "AND t.contributionType IS NOT NULL " +
           "AND (:contributionTypeId IS NULL OR t.contributionType.id = :contributionTypeId) " +
           "AND (:memberId IS NULL OR t.member.id = :memberId) " +
           "AND (:startDate IS NULL OR t.date >= :startDate) " +
           "AND (:endDate IS NULL OR t.date <= :endDate) " +
           "AND (:status IS NULL OR c.status = :status) " +
           "ORDER BY t.date DESC, t.id DESC")
    List<Transaction> findAllContributionTransactionsWithStatus(
            @Param("tenantId") Long tenantId,
            @Param("contributionTypeId") Long contributionTypeId,
            @Param("memberId") Long memberId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("status") Contribution.ContributionStatus status);
    
    /**
     * Sum credits with status filter.
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "JOIN Contribution c ON c.id = t.referenceId AND t.referenceType = 'CONTRIBUTION' " +
           "WHERE t.tenant.id = :tenantId " +
           "AND t.contributionType IS NOT NULL " +
           "AND (:contributionTypeId IS NULL OR t.contributionType.id = :contributionTypeId) " +
           "AND t.direction = 'IN' " +
           "AND (:memberId IS NULL OR t.member.id = :memberId) " +
           "AND (:startDate IS NULL OR t.date >= :startDate) " +
           "AND (:endDate IS NULL OR t.date <= :endDate) " +
           "AND (:status IS NULL OR c.status = :status)")
    BigDecimal sumContributionCreditsWithStatus(
            @Param("tenantId") Long tenantId,
            @Param("contributionTypeId") Long contributionTypeId,
            @Param("memberId") Long memberId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("status") Contribution.ContributionStatus status);
    
    /**
     * Sum debits with status filter.
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "JOIN Contribution c ON c.id = t.referenceId AND t.referenceType = 'CONTRIBUTION' " +
           "WHERE t.tenant.id = :tenantId " +
           "AND t.contributionType IS NOT NULL " +
           "AND (:contributionTypeId IS NULL OR t.contributionType.id = :contributionTypeId) " +
           "AND t.direction = 'OUT' " +
           "AND (:memberId IS NULL OR t.member.id = :memberId) " +
           "AND (:startDate IS NULL OR t.date >= :startDate) " +
           "AND (:endDate IS NULL OR t.date <= :endDate) " +
           "AND (:status IS NULL OR c.status = :status)")
    BigDecimal sumContributionDebitsWithStatus(
            @Param("tenantId") Long tenantId,
            @Param("contributionTypeId") Long contributionTypeId,
            @Param("memberId") Long memberId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("status") Contribution.ContributionStatus status);
    
    /**
     * Count transactions with status filter.
     */
    @Query("SELECT COUNT(t) FROM Transaction t " +
           "JOIN Contribution c ON c.id = t.referenceId AND t.referenceType = 'CONTRIBUTION' " +
           "WHERE t.tenant.id = :tenantId " +
           "AND t.contributionType IS NOT NULL " +
           "AND (:contributionTypeId IS NULL OR t.contributionType.id = :contributionTypeId) " +
           "AND (:memberId IS NULL OR t.member.id = :memberId) " +
           "AND (:startDate IS NULL OR t.date >= :startDate) " +
           "AND (:endDate IS NULL OR t.date <= :endDate) " +
           "AND (:status IS NULL OR c.status = :status)")
    Long countContributionTransactionsWithStatus(
            @Param("tenantId") Long tenantId,
            @Param("contributionTypeId") Long contributionTypeId,
            @Param("memberId") Long memberId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("status") Contribution.ContributionStatus status);
    
    /**
     * Find transactions by tenant, reference type, and reference ID.
     * Used for finding related transactions (e.g., welfare fund deductions).
     */
    @Query("SELECT t FROM Transaction t " +
           "WHERE t.tenant.id = :tenantId " +
           "AND t.referenceType = :referenceType " +
           "AND t.referenceId = :referenceId")
    List<Transaction> findByTenantIdAndReferenceTypeAndReferenceId(
            @Param("tenantId") Long tenantId,
            @Param("referenceType") String referenceType,
            @Param("referenceId") Long referenceId);
}
