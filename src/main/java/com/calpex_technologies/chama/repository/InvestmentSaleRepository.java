package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.InvestmentSale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface InvestmentSaleRepository extends JpaRepository<InvestmentSale, Long> {

    List<InvestmentSale> findByInvestmentIdOrderBySaleDateDesc(Long investmentId);

    @Query("SELECT COALESCE(SUM(s.realizedGain), 0) FROM InvestmentSale s WHERE s.investment.id = :investmentId")
    BigDecimal sumRealizedGainByInvestmentId(@Param("investmentId") Long investmentId);

    @Query("SELECT COALESCE(SUM(s.totalSaleAmount), 0) FROM InvestmentSale s WHERE s.investment.id = :investmentId")
    BigDecimal sumTotalSaleAmountByInvestmentId(@Param("investmentId") Long investmentId);

    @Query("SELECT COALESCE(SUM(s.unitsSold), 0) FROM InvestmentSale s WHERE s.investment.id = :investmentId")
    BigDecimal sumUnitsSoldByInvestmentId(@Param("investmentId") Long investmentId);
}
