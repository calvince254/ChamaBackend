package com.calpex_technologies.chama.service;

import com.calpex_technologies.chama.dto.MeetingAttendeeDto;
import com.calpex_technologies.chama.dto.MeetingDto;
import com.calpex_technologies.chama.dto.PageResponse;
import com.calpex_technologies.chama.dto.RsvpRequest;
import com.calpex_technologies.chama.entity.Meeting;
import com.calpex_technologies.chama.entity.MeetingAttendance;
import com.calpex_technologies.chama.entity.Member;
import com.calpex_technologies.chama.entity.Tenant;
import com.calpex_technologies.chama.repository.FineRepository;
import com.calpex_technologies.chama.repository.MeetingAttendanceRepository;
import com.calpex_technologies.chama.repository.MeetingRepository;
import com.calpex_technologies.chama.repository.MemberRepository;
import com.calpex_technologies.chama.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // Default all methods to transactional read-only
public class MeetingService {

    private final MeetingRepository meetingRepository;
    private final MeetingAttendanceRepository attendanceRepository;
    private final TenantRepository tenantRepository;
    private final MemberRepository memberRepository;
    private final FineRepository fineRepository;
    private final FileStorageService fileStorageService;
    @Lazy
    private final PenaltyService penaltyService;
    
    private static final int DEFAULT_PAGE_SIZE = 10;

    public List<MeetingDto> getAllMeetings(Long tenantId) {
        return meetingRepository.findByTenantIdOrderByDateDesc(tenantId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }
    
    /**
     * Get meetings with pagination and optional search.
     */
    public PageResponse<MeetingDto> getMeetingsPaginated(Long tenantId, int page, int size, String search) {
        if (size <= 0) size = DEFAULT_PAGE_SIZE;
        Pageable pageable = PageRequest.of(page, size, Sort.by("date").descending());
        
        Page<Meeting> meetingPage;
        
        if (search != null && !search.trim().isEmpty()) {
            meetingPage = meetingRepository.findByTenantIdAndSearch(tenantId, search.trim(), pageable);
        } else {
            meetingPage = meetingRepository.findByTenantIdOrderByDateDesc(tenantId, pageable);
        }
        
        Page<MeetingDto> dtoPage = meetingPage.map(this::toDto);
        return PageResponse.from(dtoPage);
    }

    public MeetingDto getMeeting(Long id, Long tenantId) {
        Meeting meeting = meetingRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Meeting not found"));
        return toDto(meeting);
    }

    /**
     * Get detailed meeting info for dashboard (includes fines summary)
     */
    public MeetingDto getMeetingDetails(Long id, Long tenantId) {
        Meeting meeting = meetingRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Meeting not found"));
        return toDtoWithFines(meeting);
    }

    @Transactional
    public MeetingDto createMeeting(MeetingDto dto, Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        List<Member> allMembers = memberRepository.findByTenantId(tenantId);
        
        // Determine which members to invite
        boolean inviteAll = dto.isInviteAll() || dto.getInvitedMemberIds() == null || dto.getInvitedMemberIds().isEmpty();
        
        List<Member> invitedMembers;
        if (inviteAll) {
            invitedMembers = allMembers;
        } else {
            invitedMembers = allMembers.stream()
                    .filter(m -> dto.getInvitedMemberIds().contains(m.getId()))
                    .collect(Collectors.toList());
        }

        int totalMembers = invitedMembers.size();

        Meeting meeting = Meeting.builder()
                .date(LocalDate.parse(dto.getDate()))
                .venue(dto.getVenue())
                .type(Meeting.MeetingType.valueOf(dto.getType()))
                .inviteAll(inviteAll)
                .attendeesCount(0)
                .totalMembers(totalMembers)
                .quorumMet(false)
                .finesCollected(BigDecimal.ZERO)
                .minutesStatus(Meeting.MinutesStatus.DRAFT)
                .agenda(dto.getAgenda())
                .tenant(tenant)
                .build();

        meeting = meetingRepository.save(meeting);

        // Create attendance records for invited members with PENDING status
        for (Member member : invitedMembers) {
            MeetingAttendance attendance = MeetingAttendance.builder()
                    .meeting(meeting)
                    .member(member)
                    .rsvpStatus(MeetingAttendance.RsvpStatus.PENDING)
                    .attended(false)
                    .build();
            attendanceRepository.save(attendance);
        }

        return toDto(meeting);
    }

    @Transactional
    public MeetingDto updateMeeting(Long id, MeetingDto dto, Long tenantId) {
        Meeting meeting = meetingRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Meeting not found"));

        meeting.setDate(LocalDate.parse(dto.getDate()));
        meeting.setVenue(dto.getVenue());
        meeting.setType(Meeting.MeetingType.valueOf(dto.getType()));
        meeting.setAttendeesCount(dto.getAttendeesCount());
        meeting.setQuorumMet(dto.isQuorumMet());
        meeting.setMinutesStatus(Meeting.MinutesStatus.valueOf(dto.getMinutesStatus()));
        meeting.setAgenda(dto.getAgenda());
        meeting.setMinutes(dto.getMinutes());

        meeting = meetingRepository.save(meeting);
        return toDto(meeting);
    }

    @Transactional
    public void deleteMeeting(Long id, Long tenantId) {
        Meeting meeting = meetingRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Meeting not found"));
        attendanceRepository.deleteByMeeting(meeting);
        meetingRepository.delete(meeting);
    }

    // ==================== Invite Management ====================

    /**
     * Add a member to the meeting (create attendance record)
     */
    @Transactional
    public MeetingAttendeeDto addMemberToMeeting(Long meetingId, Long memberId, Long tenantId) {
        Meeting meeting = meetingRepository.findByIdAndTenantId(meetingId, tenantId)
                .orElseThrow(() -> new RuntimeException("Meeting not found"));

        // Check if member already invited
        if (attendanceRepository.findByMeetingIdAndMemberId(meetingId, memberId).isPresent()) {
            throw new RuntimeException("Member is already invited to this meeting");
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        // Create attendance record
        MeetingAttendance attendance = MeetingAttendance.builder()
                .meeting(meeting)
                .member(member)
                .rsvpStatus(MeetingAttendance.RsvpStatus.PENDING)
                .attended(false)
                .build();
        attendance = attendanceRepository.save(attendance);

        // Update meeting total
        meeting.setTotalMembers(meeting.getTotalMembers() + 1);
        meeting.setInviteAll(false); // Mark as selective since we're manually adding
        meetingRepository.save(meeting);

        return toAttendeeDto(attendance);
    }

    /**
     * Remove a member from the meeting
     */
    @Transactional
    public void removeMemberFromMeeting(Long meetingId, Long memberId, Long tenantId) {
        Meeting meeting = meetingRepository.findByIdAndTenantId(meetingId, tenantId)
                .orElseThrow(() -> new RuntimeException("Meeting not found"));

        MeetingAttendance attendance = attendanceRepository.findByMeetingIdAndMemberId(meetingId, memberId)
                .orElseThrow(() -> new RuntimeException("Member is not invited to this meeting"));

        attendanceRepository.delete(attendance);

        // Update meeting total
        meeting.setTotalMembers(Math.max(0, meeting.getTotalMembers() - 1));
        meetingRepository.save(meeting);
    }

    /**
     * Get members not invited to the meeting (for adding)
     */
    public List<Member> getUninvitedMembers(Long meetingId, Long tenantId) {
        Meeting meeting = meetingRepository.findByIdAndTenantId(meetingId, tenantId)
                .orElseThrow(() -> new RuntimeException("Meeting not found"));

        // Get invited member IDs
        Set<Long> invitedMemberIds = attendanceRepository.findByMeetingId(meetingId).stream()
                .map(a -> a.getMember().getId())
                .collect(Collectors.toSet());

        return memberRepository.findByTenantId(tenantId).stream()
                .filter(m -> !invitedMemberIds.contains(m.getId()))
                .collect(Collectors.toList());
    }

    // ==================== RSVP Methods ====================

    /**
     * Get all attendees for a meeting with their RSVP status
     */
    public List<MeetingAttendeeDto> getMeetingAttendees(Long meetingId, Long tenantId) {
        Meeting meeting = meetingRepository.findByIdAndTenantId(meetingId, tenantId)
                .orElseThrow(() -> new RuntimeException("Meeting not found"));

        return attendanceRepository.findByMeetingId(meetingId).stream()
                .map(this::toAttendeeDto)
                .collect(Collectors.toList());
    }

    /**
     * Get current user's RSVP status for a meeting
     */
    public MeetingAttendeeDto getMyRsvp(Long meetingId, Long memberId, Long tenantId) {
        Meeting meeting = meetingRepository.findByIdAndTenantId(meetingId, tenantId)
                .orElseThrow(() -> new RuntimeException("Meeting not found"));

        MeetingAttendance attendance = attendanceRepository.findByMeetingIdAndMemberId(meetingId, memberId)
                .orElseThrow(() -> new RuntimeException("Attendance record not found"));

        return toAttendeeDto(attendance);
    }

    /**
     * Submit RSVP for a meeting (member confirms or declines attendance)
     */
    @Transactional
    public MeetingAttendeeDto submitRsvp(Long meetingId, Long memberId, RsvpRequest request, Long tenantId) {
        Meeting meeting = meetingRepository.findByIdAndTenantId(meetingId, tenantId)
                .orElseThrow(() -> new RuntimeException("Meeting not found"));

        MeetingAttendance attendance = attendanceRepository.findByMeetingIdAndMemberId(meetingId, memberId)
                .orElseThrow(() -> new RuntimeException("Attendance record not found"));

        if (request.isAttending()) {
            attendance.setRsvpStatus(MeetingAttendance.RsvpStatus.ATTENDING);
            attendance.setDeclineReason(null);
        } else {
            if (request.getDeclineReason() == null || request.getDeclineReason().trim().isEmpty()) {
                throw new RuntimeException("Please provide a reason for not attending");
            }
            attendance.setRsvpStatus(MeetingAttendance.RsvpStatus.NOT_ATTENDING);
            attendance.setDeclineReason(request.getDeclineReason());
        }
        attendance.setRespondedAt(LocalDateTime.now());

        attendance = attendanceRepository.save(attendance);

        // Update meeting's confirmed attendee count
        updateMeetingAttendeeCount(meeting);

        return toAttendeeDto(attendance);
    }

    /**
     * Mark actual attendance (used by admin after meeting)
     * Auto-imposes absence penalty if member marked absent, removes if marked present
     */
    @Transactional
    public MeetingAttendeeDto markAttendance(Long meetingId, Long memberId, boolean attended, Long tenantId) {
        Meeting meeting = meetingRepository.findByIdAndTenantId(meetingId, tenantId)
                .orElseThrow(() -> new RuntimeException("Meeting not found"));

        MeetingAttendance attendance = attendanceRepository.findByMeetingIdAndMemberId(meetingId, memberId)
                .orElseThrow(() -> new RuntimeException("Attendance record not found"));

        boolean wasAttended = attendance.isAttended();
        attendance.setAttended(attended);
        attendance.setMarkedAt(LocalDateTime.now());

        attendance = attendanceRepository.save(attendance);

        // Auto-impose or remove penalty based on attendance
        if (attended && !wasAttended) {
            // Member marked as present - remove any existing absence penalty
            penaltyService.removeMeetingAbsencePenalty(tenantId, memberId, meetingId);
        } else if (!attended) {
            // Member marked as absent - impose penalty if automatic fine type is configured
            penaltyService.imposeMeetingAbsencePenalty(tenantId, memberId, meetingId);
        }

        // Update meeting's actual attendee count
        long actualAttendees = attendanceRepository.countActualAttendanceByMeetingId(meetingId);
        meeting.setAttendeesCount((int) actualAttendees);
        meeting.setQuorumMet(actualAttendees >= (meeting.getTotalMembers() * 2 / 3));
        meetingRepository.save(meeting);

        return toAttendeeDto(attendance);
    }

    /**
     * Get RSVP summary for a meeting
     */
    public MeetingRsvpSummary getRsvpSummary(Long meetingId, Long tenantId) {
        Meeting meeting = meetingRepository.findByIdAndTenantId(meetingId, tenantId)
                .orElseThrow(() -> new RuntimeException("Meeting not found"));

        List<MeetingAttendance> attendances = attendanceRepository.findByMeetingId(meetingId);
        
        int attending = 0, notAttending = 0, pending = 0;
        for (MeetingAttendance a : attendances) {
            switch (a.getRsvpStatus()) {
                case ATTENDING -> attending++;
                case NOT_ATTENDING -> notAttending++;
                case PENDING -> pending++;
            }
        }

        return MeetingRsvpSummary.builder()
                .meetingId(meetingId)
                .totalMembers(attendances.size())
                .attending(attending)
                .notAttending(notAttending)
                .pending(pending)
                .build();
    }

    private void updateMeetingAttendeeCount(Meeting meeting) {
        long confirmedCount = attendanceRepository.countConfirmedByMeetingId(meeting.getId());
        meeting.setAttendeesCount((int) confirmedCount);
        meeting.setQuorumMet(confirmedCount >= (meeting.getTotalMembers() * 2 / 3));
        meetingRepository.save(meeting);
    }

    private MeetingAttendeeDto toAttendeeDto(MeetingAttendance attendance) {
        Member member = attendance.getMember();
        return MeetingAttendeeDto.builder()
                .id(attendance.getId())
                .memberId(member.getId())
                .memberName(member.getName())
                .memberNo(member.getMemberNo())
                .rsvpStatus(attendance.getRsvpStatus().name())
                .declineReason(attendance.getDeclineReason())
                .respondedAt(attendance.getRespondedAt() != null ? attendance.getRespondedAt().toString() : null)
                .attended(attendance.isAttended())
                .markedAt(attendance.getMarkedAt() != null ? attendance.getMarkedAt().toString() : null)
                .build();
    }

    // Inner class for RSVP summary
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class MeetingRsvpSummary {
        private Long meetingId;
        private int totalMembers;
        private int attending;
        private int notAttending;
        private int pending;
    }

    private MeetingDto toDto(Meeting meeting) {
        // Get RSVP counts
        List<MeetingAttendance> attendances = attendanceRepository.findByMeetingId(meeting.getId());
        int attending = 0, notAttending = 0, pending = 0;
        for (MeetingAttendance a : attendances) {
            switch (a.getRsvpStatus()) {
                case ATTENDING -> attending++;
                case NOT_ATTENDING -> notAttending++;
                case PENDING -> pending++;
            }
        }

        return MeetingDto.builder()
                .id(meeting.getId())
                .date(meeting.getDate().toString())
                .venue(meeting.getVenue())
                .type(meeting.getType().name())
                .inviteAll(meeting.getInviteAll() != null ? meeting.getInviteAll() : true)
                .attendeesCount(meeting.getAttendeesCount())
                .totalMembers(meeting.getTotalMembers())
                .quorumMet(meeting.isQuorumMet())
                .finesCollected(meeting.getFinesCollected())
                .minutesStatus(meeting.getMinutesStatus().name())
                .agenda(meeting.getAgenda())
                .minutes(meeting.getMinutes())
                .minutesFilePath(meeting.getMinutesFilePath())
                .minutesFileName(meeting.getMinutesFileName())
                .minutesUploadedAt(meeting.getMinutesUploadedAt() != null ? meeting.getMinutesUploadedAt().toString() : null)
                .hasMinutesFile(meeting.getMinutesFilePath() != null && !meeting.getMinutesFilePath().isEmpty())
                .rsvpAttending(attending)
                .rsvpNotAttending(notAttending)
                .rsvpPending(pending)
                .build();
    }

    private MeetingDto toDtoWithFines(Meeting meeting) {
        MeetingDto dto = toDto(meeting);
        
        // Add fines summary
        BigDecimal totalFines = fineRepository.getTotalFinesForMeeting(meeting.getId());
        BigDecimal paidFines = fineRepository.getTotalPaidFinesForMeeting(meeting.getId());
        BigDecimal unpaidFines = fineRepository.getTotalUnpaidFinesForMeeting(meeting.getId());
        
        dto.setTotalFinesImposed(totalFines != null ? totalFines : BigDecimal.ZERO);
        dto.setTotalFinesPaid(paidFines != null ? paidFines : BigDecimal.ZERO);
        dto.setTotalFinesUnpaid(unpaidFines != null ? unpaidFines : BigDecimal.ZERO);
        
        // Count all fines for the meeting
        List<com.calpex_technologies.chama.entity.Fine> fines = fineRepository.findByTenantIdAndMeetingId(meeting.getTenant().getId(), meeting.getId());
        dto.setFinesCount(fines.size());
        
        // Get invited member IDs
        List<Long> invitedIds = attendanceRepository.findByMeetingId(meeting.getId()).stream()
                .map(a -> a.getMember().getId())
                .collect(Collectors.toList());
        dto.setInvitedMemberIds(invitedIds);
        
        return dto;
    }

    // ==================== Meeting Minutes File Management ====================

    /**
     * Upload meeting minutes file
     * Accepts DOC files and stores them directly
     */
    @Transactional
    public MeetingDto uploadMinutes(Long meetingId, MultipartFile file, Long tenantId) {
        Meeting meeting = meetingRepository.findByIdAndTenantId(meetingId, tenantId)
                .orElseThrow(() -> new RuntimeException("Meeting not found"));

        // Validate file
        if (file.isEmpty()) {
            throw new RuntimeException("Please select a file to upload");
        }

        String originalFileName = file.getOriginalFilename();
        String extension = "";
        if (originalFileName != null && originalFileName.contains(".")) {
            extension = originalFileName.substring(originalFileName.lastIndexOf(".") + 1).toLowerCase();
        }

        // Only accept DOC files
        if (!"doc".equals(extension)) {
            throw new RuntimeException("Please upload a DOC file.");
        }

        // Max file size: 10MB
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new RuntimeException("File size exceeds maximum limit of 10MB");
        }

        // Delete old file if exists
        if (meeting.getMinutesFilePath() != null && !meeting.getMinutesFilePath().isEmpty()) {
            fileStorageService.deleteFile(meeting.getMinutesFilePath());
        }

        // Store the DOC file
        String filePath = fileStorageService.storeFile(file, "meeting-minutes", tenantId);

        meeting.setMinutesFilePath(filePath);
        meeting.setMinutesFileName(originalFileName);
        meeting.setMinutesUploadedAt(LocalDateTime.now());
        meeting.setMinutesStatus(Meeting.MinutesStatus.CONFIRMED);

        meeting = meetingRepository.save(meeting);
        return toDtoWithFines(meeting);
    }

    /**
     * Download meeting minutes file
     */
    public Resource downloadMinutes(Long meetingId, Long tenantId) {
        Meeting meeting = meetingRepository.findByIdAndTenantId(meetingId, tenantId)
                .orElseThrow(() -> new RuntimeException("Meeting not found"));

        if (meeting.getMinutesFilePath() == null || meeting.getMinutesFilePath().isEmpty()) {
            throw new RuntimeException("No minutes file uploaded for this meeting");
        }

        return fileStorageService.loadFileAsResource(meeting.getMinutesFilePath());
    }

    /**
     * Get the content type for the minutes file
     */
    public String getMinutesContentType(Long meetingId, Long tenantId) {
        Meeting meeting = meetingRepository.findByIdAndTenantId(meetingId, tenantId)
                .orElseThrow(() -> new RuntimeException("Meeting not found"));

        if (meeting.getMinutesFileName() == null) {
            return "application/octet-stream";
        }

        return fileStorageService.getContentType(meeting.getMinutesFileName());
    }

    /**
     * Get the minutes file name
     */
    public String getMinutesFileName(Long meetingId, Long tenantId) {
        Meeting meeting = meetingRepository.findByIdAndTenantId(meetingId, tenantId)
                .orElseThrow(() -> new RuntimeException("Meeting not found"));

        return meeting.getMinutesFileName();
    }

    /**
     * Delete meeting minutes file
     */
    @Transactional
    public MeetingDto deleteMinutes(Long meetingId, Long tenantId) {
        Meeting meeting = meetingRepository.findByIdAndTenantId(meetingId, tenantId)
                .orElseThrow(() -> new RuntimeException("Meeting not found"));

        if (meeting.getMinutesFilePath() != null && !meeting.getMinutesFilePath().isEmpty()) {
            fileStorageService.deleteFile(meeting.getMinutesFilePath());
        }

        meeting.setMinutesFilePath(null);
        meeting.setMinutesFileName(null);
        meeting.setMinutesUploadedAt(null);
        meeting.setMinutesStatus(Meeting.MinutesStatus.DRAFT);

        meeting = meetingRepository.save(meeting);
        return toDtoWithFines(meeting);
    }

    /**
     * Confirm meeting minutes (without file - just mark as confirmed)
     */
    @Transactional
    public MeetingDto confirmMinutes(Long meetingId, Long tenantId) {
        Meeting meeting = meetingRepository.findByIdAndTenantId(meetingId, tenantId)
                .orElseThrow(() -> new RuntimeException("Meeting not found"));

        meeting.setMinutesStatus(Meeting.MinutesStatus.CONFIRMED);
        meeting = meetingRepository.save(meeting);
        return toDtoWithFines(meeting);
    }

    // ==================== Mark Attendance with Selected Fines ====================

    /**
     * Mark attendance and impose selected fines when marking as absent
     * @param meetingId Meeting ID
     * @param memberId Member ID
     * @param attended Whether the member attended
     * @param fineTypeIds List of fine type IDs to impose if marking as absent (can be empty)
     * @param tenantId Tenant ID
     * @return Updated attendee DTO
     */
    @Transactional
    public MeetingAttendeeDto markAttendanceWithFines(Long meetingId, Long memberId, boolean attended, 
                                                       List<Long> fineTypeIds, Long tenantId) {
        Meeting meeting = meetingRepository.findByIdAndTenantId(meetingId, tenantId)
                .orElseThrow(() -> new RuntimeException("Meeting not found"));

        MeetingAttendance attendance = attendanceRepository.findByMeetingIdAndMemberId(meetingId, memberId)
                .orElseThrow(() -> new RuntimeException("Attendance record not found"));

        boolean wasAttended = attendance.isAttended();
        attendance.setAttended(attended);
        attendance.setMarkedAt(LocalDateTime.now());

        attendance = attendanceRepository.save(attendance);

        // Handle fines based on attendance status change
        if (attended && !wasAttended) {
            // Member marked as present - remove any existing absence penalty
            penaltyService.removeMeetingAbsencePenalty(tenantId, memberId, meetingId);
        } else if (!attended && fineTypeIds != null && !fineTypeIds.isEmpty()) {
            // Member marked as absent - impose selected fines
            for (Long fineTypeId : fineTypeIds) {
                penaltyService.imposeFineByType(tenantId, memberId, meetingId, fineTypeId);
            }
        }

        // Update meeting's actual attendee count
        long actualAttendees = attendanceRepository.countActualAttendanceByMeetingId(meetingId);
        meeting.setAttendeesCount((int) actualAttendees);
        meeting.setQuorumMet(actualAttendees >= (meeting.getTotalMembers() * 2 / 3));
        meetingRepository.save(meeting);

        return toAttendeeDto(attendance);
    }

    // ==================== Meeting Minutes Document Generation ====================

    /**
     * Generate meeting minutes HTML document with real data from the system
     * Uses Word-compatible HTML with tables and inline styles
     * @param meetingId Meeting ID
     * @param tenantId Tenant ID
     * @return HTML content for the meeting minutes document
     */
    public String generateMinutesDocument(Long meetingId, Long tenantId) {
        Meeting meeting = meetingRepository.findByIdAndTenantId(meetingId, tenantId)
                .orElseThrow(() -> new RuntimeException("Meeting not found"));
        
        Tenant tenant = meeting.getTenant();
        List<MeetingAttendance> attendances = attendanceRepository.findByMeetingId(meetingId);
        
        // Calculate attendance stats
        int presentCount = (int) attendances.stream().filter(MeetingAttendance::isAttended).count();
        
        // Fines summary
        BigDecimal totalFines = fineRepository.getTotalFinesForMeeting(meetingId);
        
        // Build attendance rows
        StringBuilder attendanceRows = new StringBuilder();
        for (MeetingAttendance a : attendances) {
            String statusColor;
            String statusText;
            if (a.isAttended()) {
                statusColor = "#2E7D5B";
                statusText = "Present";
            } else if (a.getRsvpStatus() == MeetingAttendance.RsvpStatus.NOT_ATTENDING) {
                statusColor = "#B4622F";
                statusText = "Apology sent";
            } else {
                statusColor = "#6B7570";
                statusText = "Absent";
            }
            
            attendanceRows.append(String.format(
                "<tr><td style=\"padding:8px 12px; border-bottom:1px solid #DEE3E0;\">%s</td>" +
                "<td style=\"padding:8px 12px; border-bottom:1px solid #DEE3E0;\">%s</td>" +
                "<td style=\"padding:8px 12px; border-bottom:1px solid #DEE3E0; color:%s; font-weight:600;\">%s</td></tr>",
                escapeHtml(a.getMember().getName()),
                escapeHtml(a.getMember().getMemberNo()),
                statusColor,
                statusText
            ));
        }
        
        // Quorum status
        String quorumColor = meeting.isQuorumMet() ? "#2E7D5B" : "#C1443B";
        String quorumText = meeting.isQuorumMet() 
                ? String.format("Met (%d/%d)", presentCount, attendances.size())
                : String.format("Not met (%d/%d)", presentCount, attendances.size());
        
        // Meeting type display
        String meetingType = meeting.getType().name().replace("_", " ");
        meetingType = meetingType.substring(0, 1).toUpperCase() + meetingType.substring(1).toLowerCase();
        
        // Format date
        String formattedDate = meeting.getDate().format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy"));
        
        // Fines amount formatted
        String finesAmount = totalFines != null ? String.format("%,.0f", totalFines) : "0";
        
        // Agenda handling
        String agenda = meeting.getAgenda() != null && !meeting.getAgenda().trim().isEmpty() 
                ? escapeHtml(meeting.getAgenda()).replace("\n", "<br/>") 
                : "";
        
        String agendaSection;
        if (!agenda.isEmpty()) {
            String[] agendaItems = agenda.split("<br/>");
            StringBuilder agendaList = new StringBuilder();
            int itemNum = 1;
            for (String item : agendaItems) {
                if (!item.trim().isEmpty()) {
                    agendaList.append(String.format(
                        "<tr><td style=\"padding:4px 8px; color:#8A8F8A; width:30px; vertical-align:top;\">%d.</td>" +
                        "<td style=\"padding:4px 8px;\">%s</td></tr>",
                        itemNum++, item.trim()
                    ));
                }
            }
            agendaSection = agendaList.toString();
        } else {
            agendaSection = "<tr><td style=\"padding:4px 8px; color:#8A8F8A; width:30px; vertical-align:top;\">1.</td><td style=\"padding:4px 8px;\">Confirmation of previous minutes</td></tr>" +
                           "<tr><td style=\"padding:4px 8px; color:#8A8F8A; width:30px; vertical-align:top;\">2.</td><td style=\"padding:4px 8px; color:#8A8F8A; font-style:italic;\">(Add agenda items here)</td></tr>" +
                           "<tr><td style=\"padding:4px 8px; color:#8A8F8A; width:30px; vertical-align:top;\">3.</td><td style=\"padding:4px 8px;\">Any other business</td></tr>";
        }

        return String.format("""
<html xmlns:o="urn:schemas-microsoft-com:office:office" xmlns:w="urn:schemas-microsoft-com:office:word">
<head>
<meta charset="UTF-8">
<title>Meeting Minutes - %s</title>
<!--[if gte mso 9]>
<xml>
<w:WordDocument>
<w:View>Print</w:View>
<w:Zoom>100</w:Zoom>
</w:WordDocument>
</xml>
<![endif]-->
<style>
body { font-family: Calibri, Arial, sans-serif; font-size: 11pt; color: #1B2523; margin: 0; padding: 0; }
table { border-collapse: collapse; }
p { margin: 0 0 6pt 0; }
</style>
</head>
<body>

<table align="center" width="650" cellpadding="0" cellspacing="0" style="margin:0 auto;">

  <!-- Header Banner -->
  <tr>
    <td style="background-color:#0D3B3A; padding:24px 32px; text-align:center;">
      <p style="color:#FFFFFF; font-size:20pt; font-weight:bold; letter-spacing:2px; margin:0;">%s</p>
      <p style="color:#9FC2BC; font-size:9pt; margin:4px 0 0 0;">%s · registered self-help group</p>
      <p style="color:#E2A73E; font-size:11pt; font-weight:bold; letter-spacing:2px; margin:14px 0 0 0;">MINUTES OF MEETING</p>
    </td>
  </tr>

  <!-- Content Area -->
  <tr>
    <td style="padding:24px 32px;">

      <!-- Meeting Info -->
      <table width="100%%" cellpadding="0" cellspacing="0" style="margin-bottom:20px; border-bottom:1px solid #DEE3E0; padding-bottom:16px;">
        <tr>
          <td width="25%%" valign="top">
            <p style="font-size:8pt; color:#8A8F8A; text-transform:uppercase; letter-spacing:1px; margin:0;">DATE</p>
            <p style="font-size:11pt; font-weight:bold; margin:4px 0 0 0;">%s</p>
          </td>
          <td width="25%%" valign="top">
            <p style="font-size:8pt; color:#8A8F8A; text-transform:uppercase; letter-spacing:1px; margin:0;">VENUE</p>
            <p style="font-size:11pt; font-weight:bold; margin:4px 0 0 0;">%s</p>
          </td>
          <td width="25%%" valign="top">
            <p style="font-size:8pt; color:#8A8F8A; text-transform:uppercase; letter-spacing:1px; margin:0;">MEETING TYPE</p>
            <p style="font-size:11pt; font-weight:bold; margin:4px 0 0 0;">%s</p>
          </td>
          <td width="25%%" valign="top">
            <p style="font-size:8pt; color:#8A8F8A; text-transform:uppercase; letter-spacing:1px; margin:0;">QUORUM</p>
            <p style="font-size:11pt; font-weight:bold; margin:4px 0 0 0; color:%s;">%s</p>
          </td>
        </tr>
      </table>

      <!-- ATTENDANCE -->
      <table width="100%%" cellpadding="0" cellspacing="0" style="margin-bottom:20px;">
        <tr>
          <td style="border-left:4px solid #E2A73E; padding-left:10px; margin-bottom:10px;">
            <p style="font-size:10pt; font-weight:bold; color:#0D3B3A; text-transform:uppercase; letter-spacing:1px; margin:0 0 10px 0;">ATTENDANCE</p>
          </td>
        </tr>
        <tr>
          <td>
            <table width="100%%" cellpadding="0" cellspacing="0" style="font-size:10pt;">
              <tr style="background-color:#F8F9F8;">
                <th align="left" style="font-size:8pt; text-transform:uppercase; color:#8A8F8A; font-weight:600; padding:6px 12px; border-bottom:1px solid #DEE3E0;">Member</th>
                <th align="left" style="font-size:8pt; text-transform:uppercase; color:#8A8F8A; font-weight:600; padding:6px 12px; border-bottom:1px solid #DEE3E0;">ID</th>
                <th align="left" style="font-size:8pt; text-transform:uppercase; color:#8A8F8A; font-weight:600; padding:6px 12px; border-bottom:1px solid #DEE3E0;">Status</th>
              </tr>
              %s
            </table>
          </td>
        </tr>
      </table>

      <!-- AGENDA -->
      <table width="100%%" cellpadding="0" cellspacing="0" style="margin-bottom:20px;">
        <tr>
          <td style="border-left:4px solid #E2A73E; padding-left:10px;">
            <p style="font-size:10pt; font-weight:bold; color:#0D3B3A; text-transform:uppercase; letter-spacing:1px; margin:0 0 10px 0;">AGENDA</p>
          </td>
        </tr>
        <tr>
          <td>
            <table width="100%%" cellpadding="0" cellspacing="0" style="font-size:10pt;">
              %s
            </table>
          </td>
        </tr>
      </table>

      <!-- DISCUSSION & RESOLUTIONS -->
      <table width="100%%" cellpadding="0" cellspacing="0" style="margin-bottom:20px;">
        <tr>
          <td style="border-left:4px solid #E2A73E; padding-left:10px;">
            <p style="font-size:10pt; font-weight:bold; color:#0D3B3A; text-transform:uppercase; letter-spacing:1px; margin:0 0 10px 0;">DISCUSSION &amp; RESOLUTIONS</p>
          </td>
        </tr>
        <tr>
          <td style="font-size:10pt;">
            <p style="margin:0 0 4px 0;">1. The previous meeting's minutes were reviewed.</p>
            <p style="margin:0 0 12px 20px; padding:6px 10px; background-color:#FBFBFA; border:1px solid #DEE3E0;"><b>Resolved:</b> <i style="color:#8A8F8A;">(Enter resolution here)</i></p>
            
            <p style="margin:0 0 4px 0;">2. <i style="color:#8A8F8A;">(Enter discussion point here)</i></p>
            <p style="margin:0 0 12px 20px; padding:6px 10px; background-color:#FBFBFA; border:1px solid #DEE3E0;"><b>Resolved:</b> <i style="color:#8A8F8A;">(Enter resolution here)</i></p>
            
            <p style="margin:0 0 4px 0;">3. <i style="color:#8A8F8A;">(Enter discussion point here)</i></p>
            <p style="margin:0 0 12px 20px; padding:6px 10px; background-color:#FBFBFA; border:1px solid #DEE3E0;"><b>Resolved:</b> <i style="color:#8A8F8A;">(Enter resolution here)</i></p>
            
            <p style="margin:0;">4. No other business was raised.</p>
          </td>
        </tr>
      </table>

      <!-- FINANCIAL SUMMARY -->
      <table width="100%%" cellpadding="0" cellspacing="0" style="margin-bottom:20px;">
        <tr>
          <td style="border-left:4px solid #E2A73E; padding-left:10px;">
            <p style="font-size:10pt; font-weight:bold; color:#0D3B3A; text-transform:uppercase; letter-spacing:1px; margin:0 0 10px 0;">FINANCIAL SUMMARY</p>
          </td>
        </tr>
        <tr>
          <td>
            <table width="100%%" cellpadding="0" cellspacing="0" border="1" style="border-collapse:collapse; border-color:#DEE3E0;">
              <tr>
                <td width="33%%" align="center" style="padding:12px; border:1px solid #DEE3E0;">
                  <p style="font-size:8pt; color:#8A8F8A; text-transform:uppercase; margin:0;">Contributions Collected</p>
                  <p style="font-size:14pt; font-weight:bold; color:#2E7D5B; margin:4px 0 0 0;">KES _________</p>
                </td>
                <td width="33%%" align="center" style="padding:12px; border:1px solid #DEE3E0;">
                  <p style="font-size:8pt; color:#8A8F8A; text-transform:uppercase; margin:0;">Fines Issued</p>
                  <p style="font-size:14pt; font-weight:bold; color:#C1443B; margin:4px 0 0 0;">KES %s</p>
                </td>
                <td width="34%%" align="center" style="padding:12px; border:1px solid #DEE3E0;">
                  <p style="font-size:8pt; color:#8A8F8A; text-transform:uppercase; margin:0;">Loans Disbursed</p>
                  <p style="font-size:14pt; font-weight:bold; color:#1B2523; margin:4px 0 0 0;">KES _________</p>
                </td>
              </tr>
            </table>
          </td>
        </tr>
      </table>

      <!-- ACTION ITEMS -->
      <table width="100%%" cellpadding="0" cellspacing="0" style="margin-bottom:20px;">
        <tr>
          <td style="border-left:4px solid #E2A73E; padding-left:10px;">
            <p style="font-size:10pt; font-weight:bold; color:#0D3B3A; text-transform:uppercase; letter-spacing:1px; margin:0 0 10px 0;">ACTION ITEMS</p>
          </td>
        </tr>
        <tr>
          <td>
            <table width="100%%" cellpadding="0" cellspacing="0" border="1" style="border-collapse:collapse; border-color:#DEE3E0; font-size:10pt;">
              <tr style="background-color:#F8F9F8;">
                <th align="left" width="50%%" style="font-size:8pt; text-transform:uppercase; color:#8A8F8A; font-weight:600; padding:6px 12px; border:1px solid #DEE3E0;">Action</th>
                <th align="left" width="25%%" style="font-size:8pt; text-transform:uppercase; color:#8A8F8A; font-weight:600; padding:6px 12px; border:1px solid #DEE3E0;">Owner</th>
                <th align="left" width="25%%" style="font-size:8pt; text-transform:uppercase; color:#8A8F8A; font-weight:600; padding:6px 12px; border:1px solid #DEE3E0;">Due</th>
              </tr>
              <tr>
                <td style="padding:8px 12px; border:1px solid #DEE3E0; color:#8A8F8A; font-style:italic;">(Enter action item)</td>
                <td style="padding:8px 12px; border:1px solid #DEE3E0; color:#8A8F8A; font-style:italic;">(Owner)</td>
                <td style="padding:8px 12px; border:1px solid #DEE3E0; color:#8A8F8A; font-style:italic;">(Date)</td>
              </tr>
              <tr>
                <td style="padding:8px 12px; border:1px solid #DEE3E0; color:#8A8F8A; font-style:italic;">(Enter action item)</td>
                <td style="padding:8px 12px; border:1px solid #DEE3E0; color:#8A8F8A; font-style:italic;">(Owner)</td>
                <td style="padding:8px 12px; border:1px solid #DEE3E0; color:#8A8F8A; font-style:italic;">(Date)</td>
              </tr>
            </table>
          </td>
        </tr>
      </table>

      <!-- NEXT MEETING -->
      <table width="100%%" cellpadding="0" cellspacing="0" style="margin-bottom:20px;">
        <tr>
          <td style="border-left:4px solid #E2A73E; padding-left:10px;">
            <p style="font-size:10pt; font-weight:bold; color:#0D3B3A; text-transform:uppercase; letter-spacing:1px; margin:0 0 10px 0;">NEXT MEETING</p>
          </td>
        </tr>
        <tr>
          <td style="padding:12px 16px; background-color:#FBFBFA; border:1px solid #DEE3E0;">
            <i style="color:#8A8F8A;">(Enter next meeting date, venue and type)</i>
            <span style="color:#8A8F8A; font-size:9pt; float:right;">(notice to be sent 7 days prior)</span>
          </td>
        </tr>
      </table>

      <!-- CONFIRMATION / SIGNATURES -->
      <table width="100%%" cellpadding="0" cellspacing="0" style="margin-bottom:20px;">
        <tr>
          <td style="border-left:4px solid #E2A73E; padding-left:10px;">
            <p style="font-size:10pt; font-weight:bold; color:#0D3B3A; text-transform:uppercase; letter-spacing:1px; margin:0 0 14px 0;">CONFIRMATION</p>
          </td>
        </tr>
        <tr>
          <td>
            <table width="100%%" cellpadding="0" cellspacing="0">
              <tr>
                <td width="45%%" valign="top">
                  <p align="center" style="font-weight:bold; margin:0 0 14px 0;">SECRETARY</p>
                  <p style="margin:0 0 4px 0; font-size:10pt; color:#6B7570;">Name:</p>
                  <p style="border-bottom:1px solid #1B2523; height:20px; margin:0 0 10px 0;">&nbsp;</p>
                  <p style="margin:0 0 4px 0; font-size:10pt; color:#6B7570;">Signature:</p>
                  <p style="border-bottom:1px solid #1B2523; height:20px; margin:0 0 10px 0;">&nbsp;</p>
                  <p style="margin:0 0 4px 0; font-size:10pt; color:#6B7570;">Date:</p>
                  <p style="border-bottom:1px solid #1B2523; height:20px; margin:0;">&nbsp;</p>
                </td>
                <td width="10%%">&nbsp;</td>
                <td width="45%%" valign="top">
                  <p align="center" style="font-weight:bold; margin:0 0 14px 0;">CHAIRPERSON</p>
                  <p style="margin:0 0 4px 0; font-size:10pt; color:#6B7570;">Name:</p>
                  <p style="border-bottom:1px solid #1B2523; height:20px; margin:0 0 10px 0;">&nbsp;</p>
                  <p style="margin:0 0 4px 0; font-size:10pt; color:#6B7570;">Signature:</p>
                  <p style="border-bottom:1px solid #1B2523; height:20px; margin:0 0 10px 0;">&nbsp;</p>
                  <p style="margin:0 0 4px 0; font-size:10pt; color:#6B7570;">Date:</p>
                  <p style="border-bottom:1px solid #1B2523; height:20px; margin:0;">&nbsp;</p>
                </td>
              </tr>
            </table>
          </td>
        </tr>
      </table>

    </td>
  </tr>

  <!-- Footer -->
  <tr>
    <td align="center" style="padding:12px; color:#8A8F8A; font-size:9pt;">
      Generated by ChamaManager · Calpex Technologies
    </td>
  </tr>

</table>

</body>
</html>
                """,
                escapeHtml(tenant.getName()),                    // title
                escapeHtml(tenant.getName()).toUpperCase(),      // header name
                escapeHtml(tenant.getCode()),                    // code
                formattedDate,                                   // date
                escapeHtml(meeting.getVenue()),                  // venue
                meetingType,                                     // meeting type
                quorumColor,                                     // quorum color
                quorumText,                                      // quorum text
                attendanceRows.toString(),                       // attendance rows
                agendaSection,                                   // agenda items
                finesAmount                                      // fines amount
        );
    }
    
    /**
     * Simple HTML escape to prevent XSS
     */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
}
