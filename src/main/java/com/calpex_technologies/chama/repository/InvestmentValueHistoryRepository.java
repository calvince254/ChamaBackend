package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.InvestmentValueHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvestmentValueHistoryRepository extends JpaRepository<InvestmentValueHistory, Long> {
    
    List<InvestmentValueHistory> findByInvestmentIdOrderByValueDateDesc(Long investmentId);
    
    List<InvestmentValueHistory> findByInvestmentIdOrderByValueDateAsc(Long investmentId);
    
    Optional<InvestmentValueHistory> findTopByInvestmentIdOrderByValueDateDesc(Long investmentId);
    
    @Query("SELECT h FROM InvestmentValueHistory h WHERE h.investment.id = :investmentId AND h.valueDate >= :fromDate ORDER BY h.valueDate ASC")
    List<InvestmentValueHistory> findByInvestmentIdAndDateRange(Long investmentId, LocalDate fromDate);
    
    long countByInvestmentId(Long investmentId);
    
    void deleteByInvestmentId(Long investmentId);
}
