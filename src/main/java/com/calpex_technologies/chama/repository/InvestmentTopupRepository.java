package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.InvestmentTopup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface InvestmentTopupRepository extends JpaRepository<InvestmentTopup, Long> {
    
    List<InvestmentTopup> findByInvestmentIdOrderByTopupDateDesc(Long investmentId);
    
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM InvestmentTopup t WHERE t.investment.id = :investmentId")
    BigDecimal sumTopupsByInvestmentId(@Param("investmentId") Long investmentId);
    
    long countByInvestmentId(Long investmentId);
}
