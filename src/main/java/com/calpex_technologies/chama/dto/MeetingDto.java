package com.calpex_technologies.chama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingDto {
    private Long id;
    private String date;
    private String venue;
    private String type;
    private boolean inviteAll;       // true = all members, false = selective
    private int attendeesCount;      // Actual attendance count after meeting
    private int totalMembers;        // Total invited members
    private boolean quorumMet;
    private BigDecimal finesCollected;
    private String minutesStatus;
    private String agenda;
    private String minutes;
    
    // Minutes file fields
    private String minutesFilePath;
    private String minutesFileName;
    private String minutesUploadedAt;
    private boolean hasMinutesFile;
    
    // RSVP summary fields
    private int rsvpAttending;       // Count of members who confirmed attending
    private int rsvpNotAttending;    // Count of members who declined
    private int rsvpPending;         // Count of members who haven't responded
    
    // Fines summary (for meeting dashboard)
    private BigDecimal totalFinesImposed;
    private BigDecimal totalFinesPaid;
    private BigDecimal totalFinesUnpaid;
    private int finesCount;
    
    // Selected member IDs for creation (when inviteAll=false)
    private List<Long> invitedMemberIds;
}
