package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.MerryGoRoundRound;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MerryGoRoundRoundRepository extends JpaRepository<MerryGoRoundRound, Long> {
    List<MerryGoRoundRound> findByCycleIdOrderByPosition(Long cycleId);
    
    List<MerryGoRoundRound> findByRecipientId(Long recipientId);
    
    @Query("SELECT r FROM MerryGoRoundRound r WHERE r.cycle.tenant.id = :tenantId ORDER BY r.payoutDate DESC")
    List<MerryGoRoundRound> findByTenantIdOrderByPayoutDateDesc(Long tenantId);
}
