package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.MerryGoRoundCycle;
import com.calpex_technologies.chama.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MerryGoRoundCycleRepository extends JpaRepository<MerryGoRoundCycle, Long> {
    List<MerryGoRoundCycle> findByTenantIdOrderByStartDateDesc(Long tenantId);
    Optional<MerryGoRoundCycle> findByIdAndTenantId(Long id, Long tenantId);

    @Modifying
    void deleteByTenant(Tenant tenant);
}
