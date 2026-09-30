package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.WelfareContribution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface WelfareContributionRepository extends JpaRepository<WelfareContribution, Long> {

    List<WelfareContribution> findByWelfareEventIdAndTenantId(Long welfareEventId, Long tenantId);

    Page<WelfareContribution> findByWelfareEventIdAndTenantId(Long welfareEventId, Long tenantId, Pageable pageable);

    Optional<WelfareContribution> findByIdAndTenantId(Long id, Long tenantId);

    Optional<WelfareContribution> findByWelfareEventIdAndMemberIdAndTenantId(Long welfareEventId, Long memberId, Long tenantId);

    List<WelfareContribution> findByMemberId(Long memberId);
    
    /**
     * Find welfare contributions that were paid from a specific contribution record
     * @deprecated Use findByMemberIdAndSourceContributionTypeIdAndTenantId instead since deductions are from total, not specific records
     */
    @Deprecated
    List<WelfareContribution> findBySourceContributionIdAndTenantId(Long sourceContributionId, Long tenantId);
    
    /**
     * Find welfare contributions paid from a specific contribution type by a member.
     * Used to show welfare deductions in contribution reports.
     */
    @Query("SELECT wc FROM WelfareContribution wc " +
           "LEFT JOIN FETCH wc.welfareEvent we " +
           "LEFT JOIN FETCH we.member " +
           "WHERE wc.member.id = :memberId " +
           "AND wc.sourceContributionType.id = :contributionTypeId " +
           "AND wc.tenant.id = :tenantId")
    List<WelfareContribution> findByMemberIdAndSourceContributionTypeIdAndTenantId(
            @org.springframework.data.repository.query.Param("memberId") Long memberId, 
            @org.springframework.data.repository.query.Param("contributionTypeId") Long contributionTypeId, 
            @org.springframework.data.repository.query.Param("tenantId") Long tenantId);

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
    
    /**
     * Sum of amountFromFund for a specific member and contribution type.
     * Used to calculate available balance = total paid - welfare deductions.
     */
    @Query("SELECT COALESCE(SUM(wc.amountFromFund), 0) FROM WelfareContribution wc " +
           "WHERE wc.member.id = :memberId " +
           "AND wc.sourceContributionType.id = :contributionTypeId " +
           "AND wc.tenant.id = :tenantId")
    java.math.BigDecimal sumAmountFromFundByMemberAndContributionType(
            @org.springframework.data.repository.query.Param("memberId") Long memberId,
            @org.springframework.data.repository.query.Param("contributionTypeId") Long contributionTypeId,
            @org.springframework.data.repository.query.Param("tenantId") Long tenantId);
}
