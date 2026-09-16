package com.calpex_technologies.chama.controller;

import com.calpex_technologies.chama.dto.MeetingAttendeeDto;
import com.calpex_technologies.chama.dto.MeetingDto;
import com.calpex_technologies.chama.dto.MemberDto;
import com.calpex_technologies.chama.dto.PageResponse;
import com.calpex_technologies.chama.dto.RsvpRequest;
import com.calpex_technologies.chama.entity.Member;
import com.calpex_technologies.chama.security.UserPrincipal;
import com.calpex_technologies.chama.service.MeetingService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
public class MeetingController {

    private final MeetingService meetingService;

    @GetMapping
    public ResponseEntity<List<MeetingDto>> getAllMeetings(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(meetingService.getAllMeetings(principal.getTenantId()));
    }
    
    /**
     * Get meetings with pagination.
     * Default: 10 items per page.
     */
    @GetMapping("/paginated")
    public ResponseEntity<PageResponse<MeetingDto>> getMeetingsPaginated(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search) {
        PageResponse<MeetingDto> response = meetingService.getMeetingsPaginated(
                principal.getTenantId(), page, size, search);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MeetingDto> getMeeting(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(meetingService.getMeeting(id, principal.getTenantId()));
    }

    /**
     * Get detailed meeting info including fines summary (for meeting dashboard)
     */
    @GetMapping("/{id}/details")
    public ResponseEntity<MeetingDto> getMeetingDetails(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(meetingService.getMeetingDetails(id, principal.getTenantId()));
    }

    @PostMapping
    public ResponseEntity<MeetingDto> createMeeting(@RequestBody MeetingDto dto, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(meetingService.createMeeting(dto, principal.getTenantId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MeetingDto> updateMeeting(@PathVariable Long id, @RequestBody MeetingDto dto, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(meetingService.updateMeeting(id, dto, principal.getTenantId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMeeting(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        meetingService.deleteMeeting(id, principal.getTenantId());
        return ResponseEntity.noContent().build();
    }

    // ==================== Invite Management Endpoints ====================

    /**
     * Add a member to the meeting
     */
    @PostMapping("/{meetingId}/invites/{memberId}")
    public ResponseEntity<MeetingAttendeeDto> addMemberToMeeting(
            @PathVariable Long meetingId,
            @PathVariable Long memberId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(meetingService.addMemberToMeeting(meetingId, memberId, principal.getTenantId()));
    }

    /**
     * Remove a member from the meeting
     */
    @DeleteMapping("/{meetingId}/invites/{memberId}")
    public ResponseEntity<Void> removeMemberFromMeeting(
            @PathVariable Long meetingId,
            @PathVariable Long memberId,
            @AuthenticationPrincipal UserPrincipal principal) {
        meetingService.removeMemberFromMeeting(meetingId, memberId, principal.getTenantId());
        return ResponseEntity.ok().build();
    }

    /**
     * Get members not yet invited to the meeting
     */
    @GetMapping("/{meetingId}/uninvited-members")
    public ResponseEntity<List<MemberDto>> getUninvitedMembers(
            @PathVariable Long meetingId,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<Member> members = meetingService.getUninvitedMembers(meetingId, principal.getTenantId());
        List<MemberDto> dtos = members.stream()
                .map(m -> MemberDto.builder()
                        .id(m.getId())
                        .name(m.getName())
                        .memberNo(m.getMemberNo())
                        .phone(m.getPhone())
                        .email(m.getEmail())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    // ==================== RSVP Endpoints ====================

    /**
     * Get all attendees for a meeting with their RSVP status
     */
    @GetMapping("/{id}/attendees")
    public ResponseEntity<List<MeetingAttendeeDto>> getMeetingAttendees(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(meetingService.getMeetingAttendees(id, principal.getTenantId()));
    }

    /**
     * Get current user's RSVP status for a meeting
     */
    @GetMapping("/{id}/my-rsvp")
    public ResponseEntity<MeetingAttendeeDto> getMyRsvp(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(meetingService.getMyRsvp(id, principal.getMemberId(), principal.getTenantId()));
    }

    /**
     * Submit RSVP for a meeting (member confirms or declines attendance)
     */
    @PostMapping("/{id}/rsvp")
    public ResponseEntity<MeetingAttendeeDto> submitRsvp(
            @PathVariable Long id,
            @RequestBody RsvpRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(meetingService.submitRsvp(id, principal.getMemberId(), request, principal.getTenantId()));
    }

    /**
     * Mark actual attendance for a member (admin only, after meeting)
     */
    @PostMapping("/{meetingId}/attendance/{memberId}")
    public ResponseEntity<MeetingAttendeeDto> markAttendance(
            @PathVariable Long meetingId,
            @PathVariable Long memberId,
            @RequestParam boolean attended,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(meetingService.markAttendance(meetingId, memberId, attended, principal.getTenantId()));
    }

    /**
     * Get RSVP summary for a meeting
     */
    @GetMapping("/{id}/rsvp-summary")
    public ResponseEntity<MeetingService.MeetingRsvpSummary> getRsvpSummary(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(meetingService.getRsvpSummary(id, principal.getTenantId()));
    }

    // ==================== Meeting Minutes Endpoints ====================

    /**
     * Upload meeting minutes document
     */
    @PostMapping("/{id}/minutes/upload")
    public ResponseEntity<MeetingDto> uploadMinutes(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(meetingService.uploadMinutes(id, file, principal.getTenantId()));
    }

    /**
     * Download meeting minutes document
     */
    @GetMapping("/{id}/minutes/download")
    public ResponseEntity<Resource> downloadMinutes(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        Resource resource = meetingService.downloadMinutes(id, principal.getTenantId());
        String contentType = meetingService.getMinutesContentType(id, principal.getTenantId());
        String fileName = meetingService.getMinutesFileName(id, principal.getTenantId());
        
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(resource);
    }

    /**
     * View meeting minutes document (inline)
     */
    @GetMapping("/{id}/minutes/view")
    public ResponseEntity<Resource> viewMinutes(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        Resource resource = meetingService.downloadMinutes(id, principal.getTenantId());
        String contentType = meetingService.getMinutesContentType(id, principal.getTenantId());
        String fileName = meetingService.getMinutesFileName(id, principal.getTenantId());
        
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                .body(resource);
    }

    /**
     * Delete meeting minutes document
     */
    @DeleteMapping("/{id}/minutes")
    public ResponseEntity<MeetingDto> deleteMinutes(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(meetingService.deleteMinutes(id, principal.getTenantId()));
    }

    /**
     * Confirm meeting minutes (mark as confirmed without uploading file)
     */
    @PostMapping("/{id}/minutes/confirm")
    public ResponseEntity<MeetingDto> confirmMinutes(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(meetingService.confirmMinutes(id, principal.getTenantId()));
    }

    // ==================== Attendance with Fines Endpoint ====================

    /**
     * Mark attendance with selected fines (when marking as absent, select which fines to impose)
     */
    @PostMapping("/{meetingId}/attendance/{memberId}/with-fines")
    public ResponseEntity<MeetingAttendeeDto> markAttendanceWithFines(
            @PathVariable Long meetingId,
            @PathVariable Long memberId,
            @RequestParam boolean attended,
            @RequestParam(required = false) List<Long> fineTypeIds,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(meetingService.markAttendanceWithFines(
                meetingId, memberId, attended, fineTypeIds, principal.getTenantId()));
    }

    // ==================== Meeting Minutes Generation ====================

    /**
     * Generate meeting minutes document as a .doc file for editing in Word
     */
    @GetMapping("/{id}/minutes/generate")
    public ResponseEntity<byte[]> generateMinutesDocument(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        String html = meetingService.generateMinutesDocument(id, principal.getTenantId());
        byte[] bytes = html.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/msword"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"meeting-minutes.doc\"")
                .body(bytes);
    }
}
