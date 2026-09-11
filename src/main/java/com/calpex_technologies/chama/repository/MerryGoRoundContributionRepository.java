package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.MerryGoRoundContribution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface MerryGoRoundContributionRepository extends JpaRepository<MerryGoRoundContribution, Long> {
    
    List<MerryGoRoundContribution> findByCycleIdOrderByDueDateDesc(Long cycleId);
    
    List<MerryGoRoundContribution> findByRoundIdOrderByMemberName(Long roundId);
    
    List<MerryGoRoundContribution> findByMemberIdAndCycleId(Long memberId, Long cycleId);
    
    List<MerryGoRoundContribution> findByRoundId(Long roundId);
    
    List<MerryGoRoundContribution> findByMemberId(Long memberId);
    
    @Query("SELECT c FROM MerryGoRoundContribution c WHERE c.cycle.id = :cycleId AND c.status = 'PENDING' AND c.dueDate < :date")
    List<MerryGoRoundContribution> findOverdueContributions(Long cycleId, LocalDate date);
    
    @Query("SELECT c FROM MerryGoRoundContribution c WHERE c.status = 'PENDING' AND c.dueDate < :date AND c.cycle.autoImposeFine = true")
    List<MerryGoRoundContribution> findAllOverdueForAutoFine(LocalDate date);
    
    @Query("SELECT SUM(c.amount) FROM MerryGoRoundContribution c WHERE c.cycle.id = :cycleId AND c.status = 'PAID'")
    java.math.BigDecimal sumPaidContributions(Long cycleId);
    
    @Query("SELECT SUM(c.amount) FROM MerryGoRoundContribution c WHERE c.cycle.id = :cycleId AND c.status = 'PENDING'")
    java.math.BigDecimal sumPendingContributions(Long cycleId);
    
    @Query("SELECT COUNT(c) FROM MerryGoRoundContribution c WHERE c.round.id = :roundId AND c.status = 'PAID'")
    int countPaidForRound(Long roundId);
    
    @Query("SELECT COUNT(c) FROM MerryGoRoundContribution c WHERE c.round.id = :roundId")
    int countTotalForRound(Long roundId);
}
