package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.Member;
import com.calpex_technologies.chama.entity.Payout;
import com.calpex_technologies.chama.entity.PayoutMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface PayoutMemberRepository extends JpaRepository<PayoutMember, Long> {
    
    List<PayoutMember> findByPayoutId(Long payoutId);
    
    List<PayoutMember> findByPayoutIdOrderByPayoutAmountDesc(Long payoutId);
    
    Optional<PayoutMember> findByPayoutIdAndMemberId(Long payoutId, Long memberId);
    
    // Get all payouts for a specific member
    @Query("SELECT pm FROM PayoutMember pm JOIN pm.payout p WHERE pm.member.id = :memberId AND p.tenant.id = :tenantId ORDER BY p.createdAt DESC")
    List<PayoutMember> findByMemberIdAndTenantId(@Param("memberId") Long memberId, @Param("tenantId") Long tenantId);
    
    // Get completed payouts for a member
    @Query("SELECT pm FROM PayoutMember pm WHERE pm.member.id = :memberId AND pm.status = 'PAID' ORDER BY pm.paidAt DESC")
    List<PayoutMember> findPaidByMemberId(@Param("memberId") Long memberId);
    
    // Sum of all payouts received by a member
    @Query("SELECT COALESCE(SUM(pm.payoutAmount), 0) FROM PayoutMember pm WHERE pm.member.id = :memberId AND pm.status = 'PAID'")
    BigDecimal sumPaidToMember(@Param("memberId") Long memberId);
    
    // Count pending payouts for a payout
    @Query("SELECT COUNT(pm) FROM PayoutMember pm WHERE pm.payout.id = :payoutId AND pm.status = 'PENDING'")
    long countPendingByPayoutId(@Param("payoutId") Long payoutId);
    
    // Count paid payouts for a payout
    @Query("SELECT COUNT(pm) FROM PayoutMember pm WHERE pm.payout.id = :payoutId AND pm.status = 'PAID'")
    long countPaidByPayoutId(@Param("payoutId") Long payoutId);
    
    // Sum of all paid amounts for a tenant
    @Query("SELECT COALESCE(SUM(pm.payoutAmount), 0) FROM PayoutMember pm JOIN pm.payout p WHERE p.tenant.id = :tenantId AND pm.status = 'PAID'")
    BigDecimal sumPaidAmountsByTenantId(@Param("tenantId") Long tenantId);
    
    // Sum of paid amounts grouped by member for a tenant
    @Query("SELECT pm.member.id, COALESCE(SUM(pm.payoutAmount), 0) FROM PayoutMember pm JOIN pm.payout p WHERE p.tenant.id = :tenantId AND pm.status = 'PAID' GROUP BY pm.member.id")
    List<Object[]> sumPaidAmountsByMemberAndTenantId(@Param("tenantId") Long tenantId);
    
    @Modifying
    void deleteByPayout(Payout payout);
    
    @Modifying
    void deleteByMember(Member member);
    
    @Modifying
    @Query("DELETE FROM PayoutMember pm WHERE pm.member.id = :memberId")
    void deleteByMemberId(@Param("memberId") Long memberId);
}
