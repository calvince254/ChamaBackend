package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.Member;
import com.calpex_technologies.chama.entity.Share;
import com.calpex_technologies.chama.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShareRepository extends JpaRepository<Share, Long> {
    List<Share> findByTenantIdOrderByTotalValueDesc(Long tenantId);
    Optional<Share> findByIdAndTenantId(Long id, Long tenantId);
    Optional<Share> findByMemberIdAndTenantId(Long memberId, Long tenantId);
    
    @Query("SELECT COALESCE(SUM(s.totalValue), 0) FROM Share s WHERE s.tenant.id = :tenantId")
    BigDecimal sumTotalValueByTenantId(Long tenantId);

    @Modifying
    void deleteByTenant(Tenant tenant);

    @Modifying
    void deleteByMember(Member member);
}
