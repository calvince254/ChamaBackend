package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.WelfareContribution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface WelfareContributionRepository extends JpaRepository<WelfareContribution, Long> {

    List<WelfareContribution> findByWelfareEventIdAndTenantId(Long welfareEventId, Long tenantId);

    Optional<WelfareContribution> findByIdAndTenantId(Long id, Long tenantId);

    Optional<WelfareContribution> findByWelfareEventIdAndMemberIdAndTenantId(Long welfareEventId, Long memberId, Long tenantId);

    List<WelfareContribution> findByMemberId(Long memberId);

    @Modifying
    @Query("DELETE FROM WelfareContribution wc WHERE wc.welfareEvent.id = :welfareEventId")
    void deleteByWelfareEventId(Long welfareEventId);

    @Modifying
    @Query("DELETE FROM WelfareContribution wc WHERE wc.tenant.id = :tenantId")
    void deleteByTenant(Long tenantId);

    @Modifying
    @Query("DELETE FROM WelfareContribution wc WHERE wc.member.id = :memberId")
    void deleteByMember(Long memberId);
    
    @Query("SELECT COALESCE(SUM(wc.amountPaid), 0) FROM WelfareContribution wc WHERE wc.tenant.id = :tenantId AND wc.paid = true")
    java.math.BigDecimal sumByTenantId(@org.springframework.data.repository.query.Param("tenantId") Long tenantId);
    
    @Query("SELECT COALESCE(SUM(wc.amountPaid), 0) FROM WelfareContribution wc WHERE wc.welfareEvent.id = :eventId")
    java.math.BigDecimal sumPaidByEventId(@org.springframework.data.repository.query.Param("eventId") Long eventId);
}
