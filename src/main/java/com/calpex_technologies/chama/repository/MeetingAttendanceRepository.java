package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.Meeting;
import com.calpex_technologies.chama.entity.MeetingAttendance;
import com.calpex_technologies.chama.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MeetingAttendanceRepository extends JpaRepository<MeetingAttendance, Long> {
    
    List<MeetingAttendance> findByMeeting(Meeting meeting);
    
    List<MeetingAttendance> findByMeetingId(Long meetingId);
    
    Optional<MeetingAttendance> findByMeetingAndMember(Meeting meeting, Member member);
    
    Optional<MeetingAttendance> findByMeetingIdAndMemberId(Long meetingId, Long memberId);
    
    @Query("SELECT ma FROM MeetingAttendance ma WHERE ma.meeting.id = :meetingId AND ma.rsvpStatus = 'ATTENDING'")
    List<MeetingAttendance> findConfirmedByMeetingId(Long meetingId);
    
    @Query("SELECT ma FROM MeetingAttendance ma WHERE ma.meeting.id = :meetingId AND ma.rsvpStatus = 'NOT_ATTENDING'")
    List<MeetingAttendance> findDeclinedByMeetingId(Long meetingId);
    
    @Query("SELECT ma FROM MeetingAttendance ma WHERE ma.meeting.id = :meetingId AND ma.rsvpStatus = 'PENDING'")
    List<MeetingAttendance> findPendingByMeetingId(Long meetingId);
    
    @Query("SELECT COUNT(ma) FROM MeetingAttendance ma WHERE ma.meeting.id = :meetingId AND ma.rsvpStatus = 'ATTENDING'")
    long countConfirmedByMeetingId(Long meetingId);
    
    @Query("SELECT COUNT(ma) FROM MeetingAttendance ma WHERE ma.meeting.id = :meetingId AND ma.attended = true")
    long countActualAttendanceByMeetingId(Long meetingId);
    
    int countByMeetingIdAndAttended(Long meetingId, boolean attended);
    
    void deleteByMeeting(Meeting meeting);
}
