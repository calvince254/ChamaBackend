package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.Tenant;
import com.calpex_technologies.chama.entity.Transaction;
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
}
