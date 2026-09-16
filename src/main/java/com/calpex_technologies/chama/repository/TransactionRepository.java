package com.calpex_technologies.chama.repository;

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
}
