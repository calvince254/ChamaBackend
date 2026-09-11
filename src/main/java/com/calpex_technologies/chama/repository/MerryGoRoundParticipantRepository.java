package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.MerryGoRoundParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MerryGoRoundParticipantRepository extends JpaRepository<MerryGoRoundParticipant, Long> {
    
    List<MerryGoRoundParticipant> findByCycleIdOrderByPayoutOrder(Long cycleId);
    
    List<MerryGoRoundParticipant> findByCycleIdAndStatus(Long cycleId, MerryGoRoundParticipant.ParticipantStatus status);
    
    Optional<MerryGoRoundParticipant> findByCycleIdAndMemberId(Long cycleId, Long memberId);
    
    boolean existsByCycleIdAndMemberId(Long cycleId, Long memberId);
    
    @Query("SELECT COUNT(p) FROM MerryGoRoundParticipant p WHERE p.cycle.id = :cycleId AND p.status = 'ACTIVE'")
    int countActiveParticipants(Long cycleId);
    
    @Query("SELECT MAX(p.payoutOrder) FROM MerryGoRoundParticipant p WHERE p.cycle.id = :cycleId")
    Integer findMaxPayoutOrder(Long cycleId);
}
