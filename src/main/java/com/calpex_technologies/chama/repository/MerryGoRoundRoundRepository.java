package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.MerryGoRoundRound;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MerryGoRoundRoundRepository extends JpaRepository<MerryGoRoundRound, Long> {
    List<MerryGoRoundRound> findByCycleIdOrderByPosition(Long cycleId);
    
    List<MerryGoRoundRound> findByRecipientId(Long recipientId);
    
    @Query("SELECT r FROM MerryGoRoundRound r WHERE r.cycle.tenant.id = :tenantId ORDER BY r.payoutDate DESC")
    List<MerryGoRoundRound> findByTenantIdOrderByPayoutDateDesc(Long tenantId);
    
    @Query("SELECT r FROM MerryGoRoundRound r WHERE r.cycle.tenant.id = :tenantId AND r.payoutDate >= :today AND r.status != 'COMPLETED' ORDER BY r.payoutDate ASC")
    List<MerryGoRoundRound> findUpcomingByTenantId(@Param("tenantId") Long tenantId, @Param("today") LocalDate today);
    
    default Optional<MerryGoRoundRound> findNextPayout(Long tenantId, LocalDate today) {
        List<MerryGoRoundRound> upcoming = findUpcomingByTenantId(tenantId, today);
        return upcoming.isEmpty() ? Optional.empty() : Optional.of(upcoming.get(0));
    }
}
