package com.calpex_technologies.chama.service;

import com.calpex_technologies.chama.dto.FineDto;
import com.calpex_technologies.chama.dto.ImposeFineRequest;
import com.calpex_technologies.chama.entity.*;
import com.calpex_technologies.chama.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // Default all methods to transactional read-only
public class FineService {

    private final FineRepository fineRepository;
    private final FineTypeRepository fineTypeRepository;
    private final MeetingRepository meetingRepository;
    private final MemberRepository memberRepository;
    private final TenantRepository tenantRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public List<FineDto> getMeetingFines(Long tenantId, Long meetingId) {
        return fineRepository.findByTenantIdAndMeetingId(tenantId, meetingId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<FineDto> getMemberFines(Long tenantId, Long memberId) {
        return fineRepository.findByTenantIdAndMemberId(tenantId, memberId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<FineDto> getMemberMeetingFines(Long meetingId, Long memberId) {
        return fineRepository.findByMeetingIdAndMemberId(meetingId, memberId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public FineDto imposeFine(Long tenantId, Long meetingId, ImposeFineRequest request, Long imposedById) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new RuntimeException("Meeting not found"));

        Member member = memberRepository.findById(request.getMemberId())
                .orElseThrow(() -> new RuntimeException("Member not found"));

        FineType fineType = fineTypeRepository.findByIdAndTenantId(request.getFineTypeId(), tenantId)
                .orElseThrow(() -> new RuntimeException("Fine type not found"));

        Member imposedBy = memberRepository.findById(imposedById)
                .orElse(null);

        Fine fine = Fine.builder()
                .tenant(tenant)
                .meeting(meeting)
                .member(member)
                .fineType(fineType)
                .amount(fineType.getAmount())
                .status(Fine.FineStatus.UNPAID)
                .notes(request.getNotes())
                .imposedAt(LocalDateTime.now())
                .imposedBy(imposedBy)
                .build();

        fine = fineRepository.save(fine);

        // Update meeting fines collected total
        updateMeetingFinesTotal(meeting);

        return toDto(fine);
    }

    @Transactional
    public FineDto markFinePaid(Long tenantId, Long fineId) {
        Fine fine = fineRepository.findById(fineId)
                .orElseThrow(() -> new RuntimeException("Fine not found"));

        if (!fine.getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("Fine not found");
        }

        fine.setStatus(Fine.FineStatus.PAID);
        fine.setPaidAt(LocalDateTime.now());
        fine.setPaidAmount(fine.getAmount());
        fine = fineRepository.save(fine);

        // Update meeting fines collected total
        updateMeetingFinesTotal(fine.getMeeting());

        return toDto(fine);
    }

    @Transactional
    public FineDto recordPayment(Long tenantId, Long fineId, BigDecimal amount) {
        Fine fine = fineRepository.findById(fineId)
                .orElseThrow(() -> new RuntimeException("Fine not found"));

        if (!fine.getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("Fine not found");
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Payment amount must be greater than 0");
        }

        // Calculate the current balance
        BigDecimal currentPaid = fine.getPaidAmount() != null ? fine.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal balance = fine.getAmount().subtract(currentPaid);

        if (amount.compareTo(balance) > 0) {
            throw new RuntimeException("Payment amount cannot exceed balance of " + balance);
        }

        // Add to paid amount
        BigDecimal newPaidAmount = currentPaid.add(amount);
        fine.setPaidAmount(newPaidAmount);
        fine.setPaidAt(LocalDateTime.now());

        // Determine status based on total paid
        if (newPaidAmount.compareTo(fine.getAmount()) >= 0) {
            fine.setStatus(Fine.FineStatus.PAID);
        } else if (newPaidAmount.compareTo(BigDecimal.ZERO) > 0) {
            fine.setStatus(Fine.FineStatus.PARTIAL);
        }

        fine = fineRepository.save(fine);

        // Update meeting fines collected total
        updateMeetingFinesTotal(fine.getMeeting());

        return toDto(fine);
    }

    @Transactional
    public FineDto markFineUnpaid(Long tenantId, Long fineId) {
        Fine fine = fineRepository.findById(fineId)
                .orElseThrow(() -> new RuntimeException("Fine not found"));

        if (!fine.getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("Fine not found");
        }

        fine.setStatus(Fine.FineStatus.UNPAID);
        fine.setPaidAt(null);
        fine.setPaidAmount(null);
        fine = fineRepository.save(fine);

        // Update meeting fines collected total
        updateMeetingFinesTotal(fine.getMeeting());

        return toDto(fine);
    }

    @Transactional
    public FineDto waiveFine(Long tenantId, Long fineId) {
        Fine fine = fineRepository.findById(fineId)
                .orElseThrow(() -> new RuntimeException("Fine not found"));

        if (!fine.getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("Fine not found");
        }

        fine.setStatus(Fine.FineStatus.WAIVED);
        fine = fineRepository.save(fine);

        return toDto(fine);
    }

    @Transactional
    public void deleteFine(Long tenantId, Long fineId) {
        Fine fine = fineRepository.findById(fineId)
                .orElseThrow(() -> new RuntimeException("Fine not found"));

        if (!fine.getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("Fine not found");
        }

        Meeting meeting = fine.getMeeting();
        fineRepository.delete(fine);

        // Update meeting fines collected total
        updateMeetingFinesTotal(meeting);
    }

    private void updateMeetingFinesTotal(Meeting meeting) {
        BigDecimal paidFines = fineRepository.getTotalPaidFinesForMeeting(meeting.getId());
        meeting.setFinesCollected(paidFines != null ? paidFines : BigDecimal.ZERO);
        meetingRepository.save(meeting);
    }

    public BigDecimal getTotalFinesForMeeting(Long meetingId) {
        return fineRepository.getTotalFinesForMeeting(meetingId);
    }

    public BigDecimal getTotalPaidFinesForMeeting(Long meetingId) {
        return fineRepository.getTotalPaidFinesForMeeting(meetingId);
    }

    public BigDecimal getTotalUnpaidFinesForMeeting(Long meetingId) {
        return fineRepository.getTotalUnpaidFinesForMeeting(meetingId);
    }

    private FineDto toDto(Fine fine) {
        return FineDto.builder()
                .id(fine.getId())
                .meetingId(fine.getMeeting().getId())
                .memberId(fine.getMember().getId())
                .memberName(fine.getMember().getName())
                .memberNo(fine.getMember().getMemberNo())
                .fineTypeId(fine.getFineType().getId())
                .fineTypeName(fine.getFineType().getName())
                .amount(fine.getAmount())
                .status(fine.getStatus().name())
                .notes(fine.getNotes())
                .imposedAt(fine.getImposedAt() != null ? fine.getImposedAt().format(DATE_FORMATTER) : null)
                .imposedByName(fine.getImposedBy() != null ? fine.getImposedBy().getName() : null)
                .paidAt(fine.getPaidAt() != null ? fine.getPaidAt().format(DATE_FORMATTER) : null)
                .paidAmount(fine.getPaidAmount())
                .build();
    }
}
