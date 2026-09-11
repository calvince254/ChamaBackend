package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.Member;
import com.calpex_technologies.chama.entity.Tenant;
import com.calpex_technologies.chama.entity.WelfareEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface WelfareEventRepository extends JpaRepository<WelfareEvent, Long> {
    List<WelfareEvent> findByTenantIdOrderByDateReportedDesc(Long tenantId);
    Optional<WelfareEvent> findByIdAndTenantId(Long id, Long tenantId);
    List<WelfareEvent> findByMemberId(Long memberId);
    
    @Query("SELECT w FROM WelfareEvent w WHERE w.tenant.id = :tenantId AND w.status = 'OPEN'")
    List<WelfareEvent> findOpenByTenantId(Long tenantId);
    
    @Query("SELECT COALESCE(SUM(w.totalCollected), 0) FROM WelfareEvent w WHERE w.tenant.id = :tenantId")
    BigDecimal sumCollectedByTenantId(Long tenantId);

    @Modifying
    void deleteByTenant(Tenant tenant);

    @Modifying
    void deleteByMember(Member member);
}
