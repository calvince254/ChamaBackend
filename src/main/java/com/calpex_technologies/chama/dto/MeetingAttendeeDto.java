package com.calpex_technologies.chama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingAttendeeDto {
    private Long id;
    private Long memberId;
    private String memberName;
    private String memberNo;
    private String rsvpStatus;      // PENDING, ATTENDING, NOT_ATTENDING
    private String declineReason;
    private String respondedAt;
    private boolean attended;       // Actual attendance (marked by admin)
    private String markedAt;
}
