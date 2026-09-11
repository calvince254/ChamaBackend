package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.MerryGoRoundFine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MerryGoRoundFineRepository extends JpaRepository<MerryGoRoundFine, Long> {
    
    List<MerryGoRoundFine> findByCycleIdOrderByImposedDateDesc(Long cycleId);
    
    List<MerryGoRoundFine> findByMemberIdAndCycleId(Long memberId, Long cycleId);
    
    List<MerryGoRoundFine> findByCycleIdAndStatus(Long cycleId, MerryGoRoundFine.FineStatus status);
    
    @Query("SELECT SUM(f.amount) FROM MerryGoRoundFine f WHERE f.cycle.id = :cycleId AND f.status = 'PAID'")
    java.math.BigDecimal sumPaidFines(Long cycleId);
    
    @Query("SELECT SUM(f.amount) FROM MerryGoRoundFine f WHERE f.cycle.id = :cycleId AND f.status = 'PENDING'")
    java.math.BigDecimal sumPendingFines(Long cycleId);
    
    @Query("SELECT SUM(f.amount) FROM MerryGoRoundFine f WHERE f.member.id = :memberId AND f.cycle.id = :cycleId AND f.status = 'PENDING'")
    java.math.BigDecimal sumPendingFinesForMember(Long memberId, Long cycleId);
    
    boolean existsByContributionIdAndAutoImposed(Long contributionId, Boolean autoImposed);
    
    // Check if any fine (manual or auto) exists for a contribution
    boolean existsByContributionId(Long contributionId);
}
