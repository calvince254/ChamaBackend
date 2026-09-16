package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.Penalty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

@Repository
public interface PenaltyRepository extends JpaRepository<Penalty, Long> {
    
    List<Penalty> findByTenantIdOrderByImposedAtDesc(Long tenantId);
    
    List<Penalty> findByTenantIdAndMemberId(Long tenantId, Long memberId);
    
    List<Penalty> findByTenantIdAndStatus(Long tenantId, Penalty.PenaltyStatus status);
    
    List<Penalty> findByTenantIdAndPenaltyType(Long tenantId, Penalty.PenaltyType penaltyType);
    
    Optional<Penalty> findByIdAndTenantId(Long id, Long tenantId);
    
    // Check if penalty already exists for meeting absence
    boolean existsByTenantIdAndMemberIdAndMeetingIdAndPenaltyType(
            Long tenantId, Long memberId, Long meetingId, Penalty.PenaltyType penaltyType);
    
    // Check if penalty already exists for late payment
    boolean existsByTenantIdAndMemberIdAndContributionTypeIdAndPeriodMonthAndPenaltyType(
            Long tenantId, Long memberId, Long contributionTypeId, YearMonth periodMonth, Penalty.PenaltyType penaltyType);
    
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Penalty p WHERE p.tenant.id = :tenantId AND p.status != 'WAIVED'")
    BigDecimal getTotalPenaltiesForTenant(@Param("tenantId") Long tenantId);
    
    @Query("SELECT COALESCE(SUM(p.paidAmount), 0) FROM Penalty p WHERE p.tenant.id = :tenantId")
    BigDecimal getTotalPaidPenaltiesForTenant(@Param("tenantId") Long tenantId);
    
    @Query("SELECT COALESCE(SUM(p.amount - p.paidAmount), 0) FROM Penalty p WHERE p.tenant.id = :tenantId AND p.status NOT IN ('PAID', 'WAIVED')")
    BigDecimal getTotalUnpaidPenaltiesForTenant(@Param("tenantId") Long tenantId);
    
    @Query("SELECT COALESCE(SUM(p.amount - p.paidAmount), 0) FROM Penalty p WHERE p.member.id = :memberId AND p.status NOT IN ('PAID', 'WAIVED')")
    BigDecimal getTotalUnpaidPenaltiesForMember(@Param("memberId") Long memberId);
    
    @Query("SELECT COALESCE(SUM(p.paidAmount), 0) FROM Penalty p WHERE p.member.id = :memberId")
    BigDecimal getTotalPaidPenaltiesForMember(@Param("memberId") Long memberId);
    
    // Paginated queries
    Page<Penalty> findByTenantIdOrderByImposedAtDesc(Long tenantId, Pageable pageable);
    
    @Query("SELECT p FROM Penalty p WHERE p.tenant.id = :tenantId AND p.status = :status ORDER BY p.imposedAt DESC")
    Page<Penalty> findByTenantIdAndStatus(@Param("tenantId") Long tenantId, 
                                           @Param("status") Penalty.PenaltyStatus status, 
                                           Pageable pageable);
    
    @Query("SELECT p FROM Penalty p WHERE p.tenant.id = :tenantId AND p.member.id = :memberId ORDER BY p.imposedAt DESC")
    Page<Penalty> findByTenantIdAndMemberId(@Param("tenantId") Long tenantId, 
                                             @Param("memberId") Long memberId, 
                                             Pageable pageable);
    
    @Modifying
    @Query("DELETE FROM Penalty p WHERE p.member.id = :memberId")
    void deleteByMemberId(@Param("memberId") Long memberId);
}
