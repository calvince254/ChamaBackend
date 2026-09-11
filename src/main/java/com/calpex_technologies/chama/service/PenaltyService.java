package com.calpex_technologies.chama.service;

import com.calpex_technologies.chama.dto.PenaltyDto;
import com.calpex_technologies.chama.entity.*;
import com.calpex_technologies.chama.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class PenaltyService {

    private final PenaltyRepository penaltyRepository;
    private final MemberRepository memberRepository;
    private final TenantRepository tenantRepository;
    private final MeetingRepository meetingRepository;
    private final ContributionTypeRepository contributionTypeRepository;
    private final FineTypeRepository fineTypeRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public List<PenaltyDto> getAllPenalties(Long tenantId) {
        return penaltyRepository.findByTenantIdOrderByImposedAtDesc(tenantId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<PenaltyDto> getPenaltiesByMonth(Long tenantId, YearMonth month) {
        return penaltyRepository.findByTenantIdOrderByImposedAtDesc(tenantId)
                .stream()
                .filter(p -> {
                    // For late payment penalties, filter by period month
                    if (p.getPeriodMonth() != null) {
                        return p.getPeriodMonth().equals(month);
                    }
                    // For meeting absence penalties, filter by meeting date month
                    if (p.getMeeting() != null) {
                        return YearMonth.from(p.getMeeting().getDate()).equals(month);
                    }
                    // For other penalties, filter by imposed date month
                    if (p.getImposedAt() != null) {
                        return YearMonth.from(p.getImposedAt().toLocalDate()).equals(month);
                    }
                    return false;
                })
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<PenaltyDto> getMemberPenalties(Long tenantId, Long memberId) {
        return penaltyRepository.findByTenantIdAndMemberId(tenantId, memberId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<PenaltyDto> getUnpaidPenalties(Long tenantId) {
        return penaltyRepository.findByTenantIdAndStatus(tenantId, Penalty.PenaltyStatus.UNPAID)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public PenaltyDto getPenalty(Long tenantId, Long id) {
        Penalty penalty = penaltyRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Penalty not found"));
        return toDto(penalty);
    }

    /**
     * Impose meeting absence penalty
     */
    @Transactional
    public PenaltyDto imposeMeetingAbsencePenalty(Long tenantId, Long memberId, Long meetingId) {
        // Check if penalty already exists
        if (penaltyRepository.existsByTenantIdAndMemberIdAndMeetingIdAndPenaltyType(
                tenantId, memberId, meetingId, Penalty.PenaltyType.MEETING_ABSENT)) {
            log.debug("Meeting absence penalty already exists for member {} meeting {}", memberId, meetingId);
            return null;
        }

        // Find the automatic fine type for meeting absence
        FineType fineType = fineTypeRepository.findByTenantIdAndActiveTrueAndAutomaticTrueAndTriggerType(
                tenantId, FineType.TriggerType.MEETING_ABSENT).orElse(null);

        if (fineType == null) {
            log.debug("No automatic fine type configured for meeting absence");
            return null;
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found"));
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new RuntimeException("Meeting not found"));

        String reason = String.format("Absent from meeting on %s", meeting.getDate().toString());

        Penalty penalty = Penalty.builder()
                .tenant(tenant)
                .member(member)
                .penaltyType(Penalty.PenaltyType.MEETING_ABSENT)
                .reason(reason)
                .amount(fineType.getAmount())
                .paidAmount(BigDecimal.ZERO)
                .status(Penalty.PenaltyStatus.UNPAID)
                .meeting(meeting)
                .imposedAt(LocalDateTime.now())
                .build();

        penalty = penaltyRepository.save(penalty);
        log.info("Imposed meeting absence penalty of {} on member {} for meeting {}", 
                fineType.getAmount(), memberId, meetingId);

        return toDto(penalty);
    }

    /**
     * Remove meeting absence penalty (when member is marked present)
     */
    @Transactional
    public void removeMeetingAbsencePenalty(Long tenantId, Long memberId, Long meetingId) {
        penaltyRepository.findByTenantIdAndMemberId(tenantId, memberId).stream()
                .filter(p -> p.getPenaltyType() == Penalty.PenaltyType.MEETING_ABSENT 
                        && p.getMeeting() != null 
                        && p.getMeeting().getId().equals(meetingId)
                        && p.getStatus() == Penalty.PenaltyStatus.UNPAID)
                .findFirst()
                .ifPresent(penalty -> {
                    penaltyRepository.delete(penalty);
                    log.info("Removed meeting absence penalty for member {} meeting {}", memberId, meetingId);
                });
    }

    /**
     * Impose a specific fine type for a meeting (used when marking attendance with selected fines)
     */
    @Transactional
    public PenaltyDto imposeFineByType(Long tenantId, Long memberId, Long meetingId, Long fineTypeId) {
        FineType fineType = fineTypeRepository.findById(fineTypeId)
                .orElseThrow(() -> new RuntimeException("Fine type not found"));

        // Check if this specific fine already exists for this member and meeting
        boolean exists = penaltyRepository.findByTenantIdAndMemberId(tenantId, memberId).stream()
                .anyMatch(p -> p.getMeeting() != null 
                        && p.getMeeting().getId().equals(meetingId)
                        && p.getReason() != null 
                        && p.getReason().contains(fineType.getName()));

        if (exists) {
            log.debug("Fine type {} already imposed on member {} for meeting {}", fineType.getName(), memberId, meetingId);
            return null;
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found"));
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new RuntimeException("Meeting not found"));

        // Determine penalty type based on fine type trigger
        Penalty.PenaltyType penaltyType = Penalty.PenaltyType.MEETING_ABSENT;
        if (fineType.getTriggerType() != null) {
            penaltyType = switch (fineType.getTriggerType()) {
                case MEETING_ABSENT -> Penalty.PenaltyType.MEETING_ABSENT;
                case LATE_PAYMENT -> Penalty.PenaltyType.LATE_PAYMENT;
            };
        }

        String reason = String.format("%s - Meeting on %s", fineType.getName(), meeting.getDate().toString());

        Penalty penalty = Penalty.builder()
                .tenant(tenant)
                .member(member)
                .penaltyType(penaltyType)
                .reason(reason)
                .amount(fineType.getAmount())
                .paidAmount(BigDecimal.ZERO)
                .status(Penalty.PenaltyStatus.UNPAID)
                .meeting(meeting)
                .imposedAt(LocalDateTime.now())
                .build();

        penalty = penaltyRepository.save(penalty);
        log.info("Imposed fine '{}' of {} on member {} for meeting {}", 
                fineType.getName(), fineType.getAmount(), memberId, meetingId);

        return toDto(penalty);
    }

    /**
     * Impose late payment penalty
     */
    @Transactional
    public PenaltyDto imposeLatePaymentPenalty(Long tenantId, Long memberId, Long contributionTypeId, 
                                               YearMonth periodMonth, BigDecimal amount) {
        // Check if penalty already exists
        if (penaltyRepository.existsByTenantIdAndMemberIdAndContributionTypeIdAndPeriodMonthAndPenaltyType(
                tenantId, memberId, contributionTypeId, periodMonth, Penalty.PenaltyType.LATE_PAYMENT)) {
            log.debug("Late payment penalty already exists for member {} contribution type {} period {}", 
                    memberId, contributionTypeId, periodMonth);
            return null;
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found"));
        ContributionType contributionType = contributionTypeRepository.findById(contributionTypeId)
                .orElseThrow(() -> new RuntimeException("Contribution type not found"));

        String reason = String.format("Late payment for %s - %s", 
                contributionType.getName(), periodMonth.toString());

        Penalty penalty = Penalty.builder()
                .tenant(tenant)
                .member(member)
                .penaltyType(Penalty.PenaltyType.LATE_PAYMENT)
                .reason(reason)
                .amount(amount)
                .paidAmount(BigDecimal.ZERO)
                .status(Penalty.PenaltyStatus.UNPAID)
                .contributionType(contributionType)
                .periodMonth(periodMonth)
                .imposedAt(LocalDateTime.now())
                .build();

        penalty = penaltyRepository.save(penalty);
        log.info("Imposed late payment penalty of {} on member {} for {} {}", 
                amount, memberId, contributionType.getName(), periodMonth);

        return toDto(penalty);
    }

    /**
     * Record payment for a penalty (supports partial payments)
     */
    @Transactional
    public PenaltyDto recordPayment(Long tenantId, Long penaltyId, BigDecimal amount) {
        Penalty penalty = penaltyRepository.findByIdAndTenantId(penaltyId, tenantId)
                .orElseThrow(() -> new RuntimeException("Penalty not found"));

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Payment amount must be greater than 0");
        }

        BigDecimal currentPaid = penalty.getPaidAmount() != null ? penalty.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal balance = penalty.getAmount().subtract(currentPaid);

        if (amount.compareTo(balance) > 0) {
            throw new RuntimeException("Payment amount cannot exceed balance of " + balance);
        }

        BigDecimal newPaidAmount = currentPaid.add(amount);
        penalty.setPaidAmount(newPaidAmount);
        penalty.setPaidAt(LocalDateTime.now());

        if (newPaidAmount.compareTo(penalty.getAmount()) >= 0) {
            penalty.setStatus(Penalty.PenaltyStatus.PAID);
        } else {
            penalty.setStatus(Penalty.PenaltyStatus.PARTIAL);
        }

        penalty = penaltyRepository.save(penalty);
        return toDto(penalty);
    }

    /**
     * Waive a penalty
     */
    @Transactional
    public PenaltyDto waivePenalty(Long tenantId, Long penaltyId) {
        Penalty penalty = penaltyRepository.findByIdAndTenantId(penaltyId, tenantId)
                .orElseThrow(() -> new RuntimeException("Penalty not found"));

        penalty.setStatus(Penalty.PenaltyStatus.WAIVED);
        penalty = penaltyRepository.save(penalty);
        return toDto(penalty);
    }

    /**
     * Delete a penalty
     */
    @Transactional
    public void deletePenalty(Long tenantId, Long penaltyId) {
        Penalty penalty = penaltyRepository.findByIdAndTenantId(penaltyId, tenantId)
                .orElseThrow(() -> new RuntimeException("Penalty not found"));
        penaltyRepository.delete(penalty);
    }

    // Summary methods
    public BigDecimal getTotalPenalties(Long tenantId) {
        return penaltyRepository.getTotalPenaltiesForTenant(tenantId);
    }

    public BigDecimal getTotalPaidPenalties(Long tenantId) {
        return penaltyRepository.getTotalPaidPenaltiesForTenant(tenantId);
    }

    public BigDecimal getTotalUnpaidPenalties(Long tenantId) {
        return penaltyRepository.getTotalUnpaidPenaltiesForTenant(tenantId);
    }

    private PenaltyDto toDto(Penalty penalty) {
        PenaltyDto.PenaltyDtoBuilder builder = PenaltyDto.builder()
                .id(penalty.getId())
                .memberId(penalty.getMember().getId())
                .memberName(penalty.getMember().getName())
                .memberNo(penalty.getMember().getMemberNo())
                .penaltyType(penalty.getPenaltyType().name())
                .reason(penalty.getReason())
                .amount(penalty.getAmount())
                .paidAmount(penalty.getPaidAmount())
                .balance(penalty.getAmount().subtract(
                        penalty.getPaidAmount() != null ? penalty.getPaidAmount() : BigDecimal.ZERO))
                .status(penalty.getStatus().name())
                .imposedAt(penalty.getImposedAt() != null ? penalty.getImposedAt().format(DATE_FORMATTER) : null)
                .paidAt(penalty.getPaidAt() != null ? penalty.getPaidAt().format(DATE_FORMATTER) : null);

        if (penalty.getMeeting() != null) {
            builder.meetingId(penalty.getMeeting().getId())
                   .meetingDate(penalty.getMeeting().getDate().toString());
        }

        if (penalty.getContributionType() != null) {
            builder.contributionTypeId(penalty.getContributionType().getId())
                   .contributionTypeName(penalty.getContributionType().getName());
        }

        if (penalty.getPeriodMonth() != null) {
            builder.periodMonth(penalty.getPeriodMonth().toString());
        }

        return builder.build();
    }
}
