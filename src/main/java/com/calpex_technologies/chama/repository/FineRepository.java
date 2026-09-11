package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.Fine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface FineRepository extends JpaRepository<Fine, Long> {
    
    List<Fine> findByTenantIdAndMeetingId(Long tenantId, Long meetingId);
    
    List<Fine> findByTenantIdAndMemberId(Long tenantId, Long memberId);
    
    List<Fine> findByMeetingIdAndMemberId(Long meetingId, Long memberId);
    
    List<Fine> findByMemberId(Long memberId);
    
    @Query("SELECT COALESCE(SUM(f.amount), 0) FROM Fine f WHERE f.meeting.id = :meetingId")
    BigDecimal getTotalFinesForMeeting(@Param("meetingId") Long meetingId);
    
    @Query("SELECT COALESCE(SUM(f.paidAmount), 0) FROM Fine f WHERE f.meeting.id = :meetingId")
    BigDecimal getTotalPaidFinesForMeeting(@Param("meetingId") Long meetingId);
    
    @Query("SELECT COALESCE(SUM(f.amount - COALESCE(f.paidAmount, 0)), 0) FROM Fine f WHERE f.meeting.id = :meetingId AND f.status != 'WAIVED'")
    BigDecimal getTotalUnpaidFinesForMeeting(@Param("meetingId") Long meetingId);
    
    @Query("SELECT COALESCE(SUM(f.amount - COALESCE(f.paidAmount, 0)), 0) FROM Fine f WHERE f.member.id = :memberId AND f.status != 'PAID' AND f.status != 'WAIVED'")
    BigDecimal getTotalUnpaidFinesForMember(@Param("memberId") Long memberId);
    
    long countByMeetingIdAndStatus(Long meetingId, Fine.FineStatus status);
}
