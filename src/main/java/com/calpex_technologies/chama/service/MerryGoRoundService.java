package com.calpex_technologies.chama.service;

import com.calpex_technologies.chama.dto.*;
import com.calpex_technologies.chama.entity.*;
import com.calpex_technologies.chama.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MerryGoRoundService {

    private final MerryGoRoundCycleRepository cycleRepository;
    private final MerryGoRoundRoundRepository roundRepository;
    private final MerryGoRoundParticipantRepository participantRepository;
    private final MerryGoRoundContributionRepository contributionRepository;
    private final MerryGoRoundFineRepository fineRepository;
    private final MemberRepository memberRepository;
    private final TenantRepository tenantRepository;
    private final PayableRepository payableRepository;
    private final PaymentClaimRepository paymentClaimRepository;

    // ==================== CYCLES ====================

    public List<MerryGoRoundCycleDto> getAllCycles(Long tenantId) {
        return cycleRepository.findByTenantIdOrderByStartDateDesc(tenantId).stream()
                .map(this::toCycleDto)
                .collect(Collectors.toList());
    }

    public MerryGoRoundCycleDto getCycle(Long cycleId, Long tenantId) {
        MerryGoRoundCycle cycle = cycleRepository.findById(cycleId)
                .orElseThrow(() -> new RuntimeException("Cycle not found"));
        
        if (!cycle.getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("Cycle not found");
        }
        
        return toCycleDetailDto(cycle);
    }

    @Transactional
    public MerryGoRoundCycleDto createCycle(CreateMerryGoRoundCycleRequest request, Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        // Build the cycle
        MerryGoRoundCycle cycle = MerryGoRoundCycle.builder()
                .name(request.getName())
                .contributionAmount(request.getContributionAmount())
                .startDate(LocalDate.parse(request.getStartDate()))
                .frequency(MerryGoRoundCycle.Frequency.valueOf(request.getFrequency()))
                .dayOfWeek(request.getDayOfWeek())
                .dayOfMonth(request.getDayOfMonth())
                .weekOfMonth(request.getWeekOfMonth())
                .dayOfWeekInMonth(request.getDayOfWeekInMonth())
                .latePaymentFineAmount(request.getLatePaymentFineAmount())
                .autoImposeFine(request.getAutoImposeFine() != null ? request.getAutoImposeFine() : false)
                .gracePeriodDays(request.getGracePeriodDays() != null ? request.getGracePeriodDays() : 0)
                .isRecurring(request.getIsRecurring() != null ? request.getIsRecurring() : false)
                .numberOfCycles(request.getNumberOfCycles() != null ? request.getNumberOfCycles() : 1)
                .currentCycleNumber(1)
                .status(MerryGoRoundCycle.CycleStatus.ACTIVE)
                .tenant(tenant)
                .build();
        
        cycle = cycleRepository.save(cycle);

        // Get participants - PRESERVE ORDER from request
        List<Member> participants;
        if ("ALL".equals(request.getParticipantSelection()) && 
            (request.getParticipantIds() == null || request.getParticipantIds().isEmpty())) {
            // No specific order provided, use default database order
            participants = memberRepository.findByTenantIdAndStatus(tenantId, Member.MemberStatus.ACTIVE);
        } else if (request.getParticipantIds() != null && !request.getParticipantIds().isEmpty()) {
            // Use the provided order (custom or specific selection)
            // findAllById doesn't preserve order, so we need to reorder manually
            List<Member> foundMembers = memberRepository.findAllById(request.getParticipantIds()).stream()
                    .filter(m -> m.getTenant().getId().equals(tenantId))
                    .collect(Collectors.toList());
            
            // Reorder based on the input order
            List<Long> orderedIds = request.getParticipantIds();
            participants = new ArrayList<>();
            for (Long id : orderedIds) {
                foundMembers.stream()
                        .filter(m -> m.getId().equals(id))
                        .findFirst()
                        .ifPresent(participants::add);
            }
        } else {
            // Fallback to all active members
            participants = memberRepository.findByTenantIdAndStatus(tenantId, Member.MemberStatus.ACTIVE);
        }

        if (participants.isEmpty()) {
            throw new RuntimeException("At least one participant is required");
        }

        // Add participants with payout order
        int order = 1;
        for (Member member : participants) {
            MerryGoRoundParticipant participant = MerryGoRoundParticipant.builder()
                    .cycle(cycle)
                    .member(member)
                    .payoutOrder(order++)
                    .status(MerryGoRoundParticipant.ParticipantStatus.ACTIVE)
                    .build();
            cycle.getParticipants().add(participant);
        }

        // Calculate expected amount per round (ALL participants contribute, including recipient)
        // If 3 people at 600 each, recipient gets 3 × 600 = 1800
        BigDecimal expectedAmount = request.getContributionAmount()
                .multiply(BigDecimal.valueOf(participants.size()));

        // Create rounds for each participant
        LocalDate payoutDate = LocalDate.parse(request.getStartDate());
        int position = 1;
        for (Member recipient : participants) {
            MerryGoRoundRound round = MerryGoRoundRound.builder()
                    .cycle(cycle)
                    .recipient(recipient)
                    .payoutDate(payoutDate)
                    .expectedAmount(expectedAmount)
                    .collectedAmount(BigDecimal.ZERO)
                    .paidAmount(BigDecimal.ZERO)
                    .status(MerryGoRoundRound.RoundStatus.PENDING)
                    .position(position++)
                    .build();
            cycle.getRounds().add(round);
            
            // Create contribution records for ALL participants (including recipient)
            // Everyone contributes to the pool every round
            for (Member contributor : participants) {
                MerryGoRoundContribution contribution = MerryGoRoundContribution.builder()
                        .cycle(cycle)
                        .round(round)
                        .member(contributor)
                        .amount(request.getContributionAmount())
                        .dueDate(payoutDate)
                        .status(MerryGoRoundContribution.ContributionStatus.PENDING)
                        .build();
                cycle.getContributions().add(contribution);
            }
            
            // Calculate next payout date based on frequency
            payoutDate = calculateNextDate(payoutDate, cycle);
        }

        cycle.setEndDate(payoutDate.minusDays(1));
        cycle = cycleRepository.save(cycle);

        return toCycleDto(cycle);
    }

    @Transactional
    public MerryGoRoundCycleDto updateCycle(Long cycleId, MerryGoRoundCycleDto dto, Long tenantId) {
        MerryGoRoundCycle cycle = cycleRepository.findById(cycleId)
                .orElseThrow(() -> new RuntimeException("Cycle not found"));
        
        if (!cycle.getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("Cycle not found");
        }

        if (dto.getName() != null) cycle.setName(dto.getName());
        if (dto.getLatePaymentFineAmount() != null) cycle.setLatePaymentFineAmount(dto.getLatePaymentFineAmount());
        if (dto.getAutoImposeFine() != null) cycle.setAutoImposeFine(dto.getAutoImposeFine());
        if (dto.getGracePeriodDays() != null) cycle.setGracePeriodDays(dto.getGracePeriodDays());

        cycle = cycleRepository.save(cycle);
        return toCycleDto(cycle);
    }

    @Transactional
    public void deleteCycle(Long cycleId, Long tenantId) {
        MerryGoRoundCycle cycle = cycleRepository.findById(cycleId)
                .orElseThrow(() -> new RuntimeException("Cycle not found"));
        
        if (!cycle.getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("Cycle not found");
        }

        cycleRepository.delete(cycle);
    }

    // ==================== PARTICIPANTS ====================

    public List<MerryGoRoundParticipantDto> getParticipants(Long cycleId, Long tenantId) {
        MerryGoRoundCycle cycle = cycleRepository.findById(cycleId)
                .orElseThrow(() -> new RuntimeException("Cycle not found"));
        
        if (!cycle.getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("Cycle not found");
        }

        return participantRepository.findByCycleIdOrderByPayoutOrder(cycleId).stream()
                .map(this::toParticipantDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public MerryGoRoundParticipantDto addParticipant(Long cycleId, Long memberId, Long tenantId) {
        MerryGoRoundCycle cycle = cycleRepository.findById(cycleId)
                .orElseThrow(() -> new RuntimeException("Cycle not found"));
        
        if (!cycle.getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("Cycle not found");
        }

        if (participantRepository.existsByCycleIdAndMemberId(cycleId, memberId)) {
            throw new RuntimeException("Member is already a participant");
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        Integer maxOrder = participantRepository.findMaxPayoutOrder(cycleId);
        int newOrder = (maxOrder != null ? maxOrder : 0) + 1;

        MerryGoRoundParticipant participant = MerryGoRoundParticipant.builder()
                .cycle(cycle)
                .member(member)
                .payoutOrder(newOrder)
                .status(MerryGoRoundParticipant.ParticipantStatus.ACTIVE)
                .build();

        participant = participantRepository.save(participant);
        return toParticipantDto(participant);
    }

    @Transactional
    public void removeParticipant(Long cycleId, Long memberId, Long tenantId) {
        MerryGoRoundCycle cycle = cycleRepository.findById(cycleId)
                .orElseThrow(() -> new RuntimeException("Cycle not found"));
        
        if (!cycle.getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("Cycle not found");
        }

        MerryGoRoundParticipant participant = participantRepository.findByCycleIdAndMemberId(cycleId, memberId)
                .orElseThrow(() -> new RuntimeException("Participant not found"));

        participant.setStatus(MerryGoRoundParticipant.ParticipantStatus.REMOVED);
        participantRepository.save(participant);
    }

    @Transactional
    public void reorderParticipants(Long cycleId, List<Long> memberIds, Long tenantId) {
        MerryGoRoundCycle cycle = cycleRepository.findById(cycleId)
                .orElseThrow(() -> new RuntimeException("Cycle not found"));
        
        if (!cycle.getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("Cycle not found");
        }

        int order = 1;
        for (Long memberId : memberIds) {
            MerryGoRoundParticipant participant = participantRepository.findByCycleIdAndMemberId(cycleId, memberId)
                    .orElse(null);
            if (participant != null) {
                participant.setPayoutOrder(order++);
                participantRepository.save(participant);
            }
        }
    }

    // ==================== ROUNDS ====================

    public List<MerryGoRoundRoundDto> getAllRounds(Long tenantId) {
        return roundRepository.findByTenantIdOrderByPayoutDateDesc(tenantId).stream()
                .map(this::toRoundDto)
                .collect(Collectors.toList());
    }

    public List<MerryGoRoundRoundDto> getRoundsByCycle(Long cycleId, Long tenantId) {
        MerryGoRoundCycle cycle = cycleRepository.findById(cycleId)
                .orElseThrow(() -> new RuntimeException("Cycle not found"));
        
        if (!cycle.getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("Cycle not found");
        }

        return roundRepository.findByCycleIdOrderByPosition(cycleId).stream()
                .map(this::toRoundDto)
                .collect(Collectors.toList());
    }

    public MerryGoRoundRoundDto getRoundWithContributions(Long roundId, Long tenantId) {
        MerryGoRoundRound round = roundRepository.findById(roundId)
                .orElseThrow(() -> new RuntimeException("Round not found"));
        
        if (!round.getCycle().getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("Round not found");
        }

        MerryGoRoundRoundDto dto = toRoundDto(round);
        dto.setContributions(contributionRepository.findByRoundId(roundId).stream()
                .map(this::toContributionDto)
                .collect(Collectors.toList()));
        return dto;
    }

    @Transactional
    public MerryGoRoundRoundDto makeRoundPayout(Long roundId, BigDecimal payoutAmount, Long tenantId) {
        MerryGoRoundRound round = roundRepository.findById(roundId)
                .orElseThrow(() -> new RuntimeException("Round not found"));

        if (!round.getCycle().getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("Round not found");
        }

        // Calculate available payout balance (collected - already paid)
        BigDecimal payoutBalance = round.getPayoutBalance();
        
        if (payoutBalance.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("No funds available for payout. Collected: " + round.getCollectedAmount() 
                    + ", Already Paid: " + round.getPaidAmount());
        }

        // If no specific amount provided, pay everything available
        BigDecimal amountToPay = payoutAmount != null ? payoutAmount : payoutBalance;
        
        // Can't pay more than what's available
        if (amountToPay.compareTo(payoutBalance) > 0) {
            throw new RuntimeException("Cannot pay " + amountToPay + ". Only " + payoutBalance + " available.");
        }

        // Update the paid amount
        round.setPaidAmount(round.getPaidAmount().add(amountToPay));

        // Update status based on whether fully paid
        if (round.isFullyPaid()) {
            round.setStatus(MerryGoRoundRound.RoundStatus.COMPLETED);
            
            // Update participant status
            MerryGoRoundParticipant participant = participantRepository
                    .findByCycleIdAndMemberId(round.getCycle().getId(), round.getRecipient().getId())
                    .orElse(null);
            if (participant != null) {
                participant.setStatus(MerryGoRoundParticipant.ParticipantStatus.RECEIVED_PAYOUT);
                participantRepository.save(participant);
            }
        } else {
            round.setStatus(MerryGoRoundRound.RoundStatus.PARTIAL);
        }
        
        round = roundRepository.save(round);

        // Check if all rounds complete - if so, complete the cycle (or start new cycle if recurring)
        checkAndHandleCycleCompletion(round.getCycle());

        return toRoundDto(round);
    }
    
    /**
     * Check if all rounds are complete and handle cycle completion or start new cycle
     */
    private void checkAndHandleCycleCompletion(MerryGoRoundCycle cycle) {
        List<MerryGoRoundRound> allRounds = roundRepository.findByCycleIdOrderByPosition(cycle.getId());
        boolean allComplete = allRounds.stream()
                .allMatch(r -> r.getStatus() == MerryGoRoundRound.RoundStatus.COMPLETED);
        
        if (allComplete) {
            if (cycle.getIsRecurring()) {
                // Start a new cycle iteration
                startNewCycleIteration(cycle);
            } else if (cycle.getCurrentCycleNumber() < cycle.getNumberOfCycles()) {
                // More fixed cycles to go
                startNewCycleIteration(cycle);
            } else {
                // All cycles complete
                cycle.setStatus(MerryGoRoundCycle.CycleStatus.COMPLETED);
                cycleRepository.save(cycle);
            }
        }
    }
    
    /**
     * Start a new iteration of rounds when current set completes
     */
    @Transactional
    public void startNewCycleIteration(MerryGoRoundCycle cycle) {
        cycle.setCurrentCycleNumber(cycle.getCurrentCycleNumber() + 1);
        
        List<MerryGoRoundParticipant> participants = participantRepository
                .findByCycleIdOrderByPayoutOrder(cycle.getId()).stream()
                .filter(p -> p.getStatus() != MerryGoRoundParticipant.ParticipantStatus.REMOVED)
                .collect(Collectors.toList());

        // Reset participant status to ACTIVE
        for (MerryGoRoundParticipant p : participants) {
            p.setStatus(MerryGoRoundParticipant.ParticipantStatus.ACTIVE);
            participantRepository.save(p);
        }

        // Calculate expected amount (ALL participants contribute, including recipient)
        BigDecimal expectedAmount = cycle.getContributionAmount()
                .multiply(BigDecimal.valueOf(participants.size()));

        // Get last round's date to continue from
        LocalDate lastPayoutDate = cycle.getRounds().stream()
                .map(MerryGoRoundRound::getPayoutDate)
                .max(LocalDate::compareTo)
                .orElse(LocalDate.now());
        
        LocalDate payoutDate = calculateNextDate(lastPayoutDate, cycle);
        
        int currentMaxPosition = cycle.getRounds().stream()
                .mapToInt(MerryGoRoundRound::getPosition)
                .max()
                .orElse(0);

        // Create new rounds for this iteration
        int position = currentMaxPosition + 1;
        for (MerryGoRoundParticipant participant : participants) {
            MerryGoRoundRound round = MerryGoRoundRound.builder()
                    .cycle(cycle)
                    .recipient(participant.getMember())
                    .payoutDate(payoutDate)
                    .expectedAmount(expectedAmount)
                    .collectedAmount(BigDecimal.ZERO)
                    .paidAmount(BigDecimal.ZERO)
                    .status(MerryGoRoundRound.RoundStatus.PENDING)
                    .position(position++)
                    .build();
            roundRepository.save(round);
            
            // Create contribution records for ALL participants (including recipient)
            for (MerryGoRoundParticipant contributor : participants) {
                MerryGoRoundContribution contribution = MerryGoRoundContribution.builder()
                        .cycle(cycle)
                        .round(round)
                        .member(contributor.getMember())
                        .amount(cycle.getContributionAmount())
                        .dueDate(payoutDate)
                        .status(MerryGoRoundContribution.ContributionStatus.PENDING)
                        .build();
                contributionRepository.save(contribution);
            }
            
            payoutDate = calculateNextDate(payoutDate, cycle);
        }

        cycle.setEndDate(payoutDate.minusDays(1));
        cycleRepository.save(cycle);
        
        log.info("Started new MGR cycle iteration #{} for cycle '{}'", 
                cycle.getCurrentCycleNumber(), cycle.getName());
    }

    // Keep old method for backwards compatibility but delegate to new one
    @Transactional
    public MerryGoRoundRoundDto markRoundAsPaid(Long roundId, Long tenantId) {
        return makeRoundPayout(roundId, null, tenantId);
    }

    // ==================== CONTRIBUTIONS ====================

    public List<MerryGoRoundContributionDto> getContributionsByRound(Long roundId, Long tenantId) {
        MerryGoRoundRound round = roundRepository.findById(roundId)
                .orElseThrow(() -> new RuntimeException("Round not found"));
        
        if (!round.getCycle().getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("Round not found");
        }

        List<MerryGoRoundContribution> contributions = contributionRepository.findByRoundId(roundId);
        return toContributionDtoListWithPendingClaims(contributions, tenantId);
    }

    public List<MerryGoRoundContributionDto> getContributionsByCycle(Long cycleId, Long tenantId) {
        MerryGoRoundCycle cycle = cycleRepository.findById(cycleId)
                .orElseThrow(() -> new RuntimeException("Cycle not found"));
        
        if (!cycle.getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("Cycle not found");
        }

        List<MerryGoRoundContribution> contributions = contributionRepository.findByCycleIdOrderByDueDateDesc(cycleId);
        return toContributionDtoListWithPendingClaims(contributions, tenantId);
    }

    @Transactional
    public MerryGoRoundContributionDto markContributionAsPaid(Long contributionId, String paymentRef, Long tenantId) {
        MerryGoRoundContribution contribution = contributionRepository.findById(contributionId)
                .orElseThrow(() -> new RuntimeException("Contribution not found"));

        if (!contribution.getCycle().getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("Contribution not found");
        }

        // Only update if not already paid
        if (contribution.getStatus() == MerryGoRoundContribution.ContributionStatus.PAID) {
            return toContributionDto(contribution);
        }

        contribution.setStatus(MerryGoRoundContribution.ContributionStatus.PAID);
        contribution.setPaidDate(LocalDate.now());
        contribution.setPaymentReference(paymentRef);
        contribution = contributionRepository.save(contribution);

        // Update round's collected amount
        MerryGoRoundRound round = contribution.getRound();
        round.setCollectedAmount(round.getCollectedAmount().add(contribution.getAmount()));
        roundRepository.save(round);

        return toContributionDto(contribution);
    }

    @Transactional
    public List<MerryGoRoundContributionDto> markMultipleContributionsAsPaid(List<Long> contributionIds, Long tenantId) {
        List<MerryGoRoundContributionDto> results = new ArrayList<>();
        for (Long id : contributionIds) {
            try {
                results.add(markContributionAsPaid(id, null, tenantId));
            } catch (Exception e) {
                log.warn("Failed to mark contribution {} as paid: {}", id, e.getMessage());
            }
        }
        return results;
    }

    // ==================== FINES ====================

    public List<MerryGoRoundFineDto> getFinesByCycle(Long cycleId, Long tenantId) {
        MerryGoRoundCycle cycle = cycleRepository.findById(cycleId)
                .orElseThrow(() -> new RuntimeException("Cycle not found"));
        
        if (!cycle.getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("Cycle not found");
        }

        List<MerryGoRoundFine> fines = fineRepository.findByCycleIdOrderByImposedDateDesc(cycleId);
        return toFineDtoListWithPendingClaims(fines, tenantId);
    }

    @Transactional
    public List<MerryGoRoundFineDto> imposeFine(ImposeMgrFineRequest request, Long tenantId) {
        MerryGoRoundCycle cycle = cycleRepository.findById(request.getCycleId())
                .orElseThrow(() -> new RuntimeException("Cycle not found"));
        
        if (!cycle.getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("Cycle not found");
        }

        List<MerryGoRoundFineDto> results = new ArrayList<>();

        for (Long memberId : request.getMemberIds()) {
            Member member = memberRepository.findById(memberId)
                    .orElseThrow(() -> new RuntimeException("Member not found: " + memberId));

            MerryGoRoundFine fine = MerryGoRoundFine.builder()
                    .cycle(cycle)
                    .member(member)
                    .reason(request.getReason())
                    .amount(request.getAmount())
                    .imposedDate(LocalDate.now())
                    .status(MerryGoRoundFine.FineStatus.PENDING)
                    .autoImposed(false)
                    .build();

            if (request.getContributionId() != null) {
                MerryGoRoundContribution contribution = contributionRepository.findById(request.getContributionId())
                        .orElse(null);
                fine.setContribution(contribution);
            }

            fine = fineRepository.save(fine);
            results.add(toFineDto(fine));
        }

        return results;
    }

    @Transactional
    public MerryGoRoundFineDto payFine(Long fineId, Long tenantId) {
        MerryGoRoundFine fine = fineRepository.findById(fineId)
                .orElseThrow(() -> new RuntimeException("Fine not found"));

        if (!fine.getCycle().getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("Fine not found");
        }

        fine.setStatus(MerryGoRoundFine.FineStatus.PAID);
        fine.setPaidDate(LocalDate.now());
        fine = fineRepository.save(fine);

        return toFineDto(fine);
    }

    @Transactional
    public MerryGoRoundFineDto waiveFine(Long fineId, String reason, String waivedBy, Long tenantId) {
        MerryGoRoundFine fine = fineRepository.findById(fineId)
                .orElseThrow(() -> new RuntimeException("Fine not found"));

        if (!fine.getCycle().getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("Fine not found");
        }

        fine.setStatus(MerryGoRoundFine.FineStatus.WAIVED);
        fine.setWaiveReason(reason);
        fine.setWaivedBy(waivedBy);
        fine = fineRepository.save(fine);

        return toFineDto(fine);
    }

    /**
     * Get fines for the current member only in a specific cycle.
     */
    public List<MerryGoRoundFineDto> getMyFinesByCycle(Long cycleId, Long memberId, Long tenantId) {
        MerryGoRoundCycle cycle = cycleRepository.findById(cycleId)
                .orElseThrow(() -> new RuntimeException("Cycle not found"));
        
        if (!cycle.getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("Cycle not found");
        }

        List<MerryGoRoundFine> fines = fineRepository.findByMemberIdAndCycleId(memberId, cycleId);
        return toFineDtoListWithPendingClaims(fines, tenantId);
    }

    /**
     * Initiate payment for an MGR fine using the unified payment system.
     */
    @Transactional
    public InitiatePaymentResponse initiateFinePayment(Long fineId, InitiatePaymentRequest request, Long memberId, Long tenantId) {
        MerryGoRoundFine fine = fineRepository.findById(fineId)
                .orElseThrow(() -> new RuntimeException("Fine not found"));

        if (!fine.getCycle().getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("Fine not found");
        }

        // Verify the member is paying their own fine
        if (!fine.getMember().getId().equals(memberId)) {
            throw new RuntimeException("You can only pay your own fines");
        }

        if (fine.getStatus() != MerryGoRoundFine.FineStatus.PENDING) {
            throw new RuntimeException("Fine is not pending payment");
        }

        // Check if there's already a pending payment claim
        Optional<Payable> existingPayable = payableRepository.findByPayableTypeAndPayableRefIdAndTenant_Id(
                PayableType.MGR_FINE, fineId, tenantId);
        if (existingPayable.isPresent() && existingPayable.get().getStatus() == PayableStatus.AWAITING_CONFIRMATION) {
            throw new RuntimeException("There is already a pending payment claim for this fine");
        }

        BigDecimal amount = request.getAmount() != null ? request.getAmount() : fine.getAmount();
        
        // Create or get Payable record
        Payable payable = existingPayable.orElseGet(() -> {
            Payable newPayable = Payable.builder()
                    .payableType(PayableType.MGR_FINE)
                    .payableRefId(fineId)
                    .member(fine.getMember())
                    .tenant(fine.getCycle().getTenant())
                    .amount(fine.getAmount())
                    .status(PayableStatus.OUTSTANDING)
                    .description("MGR Fine: " + fine.getReason())
                    .build();
            return payableRepository.save(newPayable);
        });

        // Handle payment based on method
        String paymentMethod = request.getPaymentMethod();
        
        if ("MANUAL".equals(paymentMethod)) {
            // Create pending claim for admin verification
            PaymentClaim claim = PaymentClaim.builder()
                    .payable(payable)
                    .payer(fine.getMember())
                    .amount(amount)
                    .referenceCode(request.getReferenceCode())
                    .paymentMethod(PaymentMethod.MANUAL)
                    .status(PaymentClaimStatus.MANUAL_REVIEW)
                    .build();
            paymentClaimRepository.save(claim);

            payable.setStatus(PayableStatus.AWAITING_CONFIRMATION);
            payableRepository.save(payable);

            return InitiatePaymentResponse.builder()
                    .status("PENDING_CONFIRMATION")
                    .statusMessage("Payment claim submitted. Awaiting admin confirmation.")
                    .awaitingVerification(true)
                    .payableId(payable.getId())
                    .build();
        } else if ("STK_PUSH".equals(paymentMethod)) {
            // For STK Push - would integrate with M-PESA here
            // For now, return a pending status
            return InitiatePaymentResponse.builder()
                    .status("STK_INITIATED")
                    .statusMessage("M-PESA prompt sent to " + request.getPhoneNumber())
                    .awaitingVerification(false)
                    .payableId(payable.getId())
                    .build();
        } else if ("IN_APP_SMS".equals(paymentMethod)) {
            // Create pending claim for SMS verification
            PaymentClaim claim = PaymentClaim.builder()
                    .payable(payable)
                    .payer(fine.getMember())
                    .amount(amount)
                    .paymentMethod(PaymentMethod.IN_APP_SMS)
                    .status(PaymentClaimStatus.PENDING_SMS)
                    .build();
            paymentClaimRepository.save(claim);

            payable.setStatus(PayableStatus.AWAITING_CONFIRMATION);
            payableRepository.save(payable);

            return InitiatePaymentResponse.builder()
                    .status("PENDING_CONFIRMATION")
                    .statusMessage("Payment claim submitted. Awaiting SMS verification.")
                    .awaitingVerification(true)
                    .payableId(payable.getId())
                    .build();
        }

        throw new RuntimeException("Unsupported payment method: " + paymentMethod);
    }

    /**
     * Admin records a direct payment for an MGR fine.
     */
    @Transactional
    public MerryGoRoundFineDto recordFinePayment(Long fineId, BigDecimal amount, Long tenantId) {
        MerryGoRoundFine fine = fineRepository.findById(fineId)
                .orElseThrow(() -> new RuntimeException("Fine not found"));

        if (!fine.getCycle().getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("Fine not found");
        }

        if (fine.getStatus() != MerryGoRoundFine.FineStatus.PENDING) {
            throw new RuntimeException("Fine is not pending payment");
        }

        // Extract values before reassignment for use in lambda
        final Member fineMember = fine.getMember();
        final Tenant fineTenant = fine.getCycle().getTenant();
        final BigDecimal fineAmount = fine.getAmount();
        final String fineReason = fine.getReason();

        // Mark fine as paid
        fine.setStatus(MerryGoRoundFine.FineStatus.PAID);
        fine.setPaidDate(LocalDate.now());
        fine = fineRepository.save(fine);

        // Update or create Payable record
        Payable payable = payableRepository.findByPayableTypeAndPayableRefIdAndTenant_Id(
                PayableType.MGR_FINE, fineId, tenantId)
                .orElseGet(() -> Payable.builder()
                        .payableType(PayableType.MGR_FINE)
                        .payableRefId(fineId)
                        .member(fineMember)
                        .tenant(fineTenant)
                        .amount(fineAmount)
                        .status(PayableStatus.OUTSTANDING)
                        .description("MGR Fine: " + fineReason)
                        .build());
        
        payable.setStatus(PayableStatus.PAID);
        payableRepository.save(payable);

        log.info("Admin recorded payment for MGR fine {}, amount: {}", fineId, amount);

        return toFineDto(fine);
    }

    /**
     * Impose a late payment fine for a specific contribution.
     * This is for manual fining - the auto-impose system will skip contributions that already have a fine.
     */
    @Transactional
    public MerryGoRoundFineDto imposeFineForContribution(Long contributionId, String reason, BigDecimal amount, Long tenantId) {
        MerryGoRoundContribution contribution = contributionRepository.findById(contributionId)
                .orElseThrow(() -> new RuntimeException("Contribution not found"));

        if (!contribution.getCycle().getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("Contribution not found");
        }

        // Check if fine already exists for this contribution
        if (fineRepository.existsByContributionId(contributionId)) {
            throw new RuntimeException("A fine has already been imposed for this contribution");
        }

        MerryGoRoundCycle cycle = contribution.getCycle();
        
        // Use provided amount or fall back to cycle's default fine amount
        BigDecimal fineAmount = amount != null ? amount : cycle.getLatePaymentFineAmount();
        if (fineAmount == null || fineAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Fine amount must be greater than zero");
        }

        MerryGoRoundFine fine = MerryGoRoundFine.builder()
                .cycle(cycle)
                .member(contribution.getMember())
                .contribution(contribution)
                .reason(reason != null ? reason : "Late payment - manually imposed")
                .amount(fineAmount)
                .imposedDate(LocalDate.now())
                .status(MerryGoRoundFine.FineStatus.PENDING)
                .autoImposed(false)
                .build();

        fine = fineRepository.save(fine);

        // Update contribution status to LATE if still pending
        if (contribution.getStatus() == MerryGoRoundContribution.ContributionStatus.PENDING) {
            contribution.setStatus(MerryGoRoundContribution.ContributionStatus.LATE);
            contributionRepository.save(contribution);
        }

        log.info("Manually imposed fine of {} on member {} for contribution {}", 
                fineAmount, contribution.getMember().getName(), contributionId);

        return toFineDto(fine);
    }

    @Transactional
    public void autoImposeFines() {
        LocalDate today = LocalDate.now();
        List<MerryGoRoundContribution> overdueContributions = contributionRepository.findAllOverdueForAutoFine(today);

        for (MerryGoRoundContribution contribution : overdueContributions) {
            MerryGoRoundCycle cycle = contribution.getCycle();
            
            // Check grace period
            LocalDate fineDate = contribution.getDueDate().plusDays(cycle.getGracePeriodDays());
            if (today.isBefore(fineDate)) {
                continue;
            }

            // Check if ANY fine (manual or auto) already exists for this contribution
            if (fineRepository.existsByContributionId(contribution.getId())) {
                continue;
            }

            // Create auto fine
            MerryGoRoundFine fine = MerryGoRoundFine.builder()
                    .cycle(cycle)
                    .member(contribution.getMember())
                    .contribution(contribution)
                    .reason("Late payment - auto imposed")
                    .amount(cycle.getLatePaymentFineAmount())
                    .imposedDate(LocalDate.now())
                    .status(MerryGoRoundFine.FineStatus.PENDING)
                    .autoImposed(true)
                    .build();

            fineRepository.save(fine);

            // Update contribution status
            contribution.setStatus(MerryGoRoundContribution.ContributionStatus.LATE);
            contributionRepository.save(contribution);

            log.info("Auto-imposed fine of {} on member {} for late MGR contribution", 
                    cycle.getLatePaymentFineAmount(), contribution.getMember().getName());
        }
    }

    // ==================== SUMMARY / REPORTS ====================

    public MerryGoRoundSummaryDto getSummary(Long tenantId) {
        List<MerryGoRoundCycle> allCycles = cycleRepository.findByTenantIdOrderByStartDateDesc(tenantId);

        BigDecimal totalContributions = BigDecimal.ZERO;
        BigDecimal totalPayouts = BigDecimal.ZERO;
        BigDecimal totalFinesCollected = BigDecimal.ZERO;
        BigDecimal totalFinesPending = BigDecimal.ZERO;
        int activeCycles = 0;
        int completedCycles = 0;

        List<MerryGoRoundCycleDto> activeCyclesList = new ArrayList<>();

        for (MerryGoRoundCycle cycle : allCycles) {
            BigDecimal cycleContributions = contributionRepository.sumPaidContributions(cycle.getId());
            BigDecimal cycleFinesPaid = fineRepository.sumPaidFines(cycle.getId());
            BigDecimal cycleFinesPending = fineRepository.sumPendingFines(cycle.getId());

            if (cycleContributions != null) totalContributions = totalContributions.add(cycleContributions);
            if (cycleFinesPaid != null) totalFinesCollected = totalFinesCollected.add(cycleFinesPaid);
            if (cycleFinesPending != null) totalFinesPending = totalFinesPending.add(cycleFinesPending);

            // Sum payouts from paid rounds
            for (MerryGoRoundRound round : cycle.getRounds()) {
                totalPayouts = totalPayouts.add(round.getPaidAmount());
            }

            if (cycle.getStatus() == MerryGoRoundCycle.CycleStatus.ACTIVE) {
                activeCycles++;
                activeCyclesList.add(toCycleDto(cycle));
            } else if (cycle.getStatus() == MerryGoRoundCycle.CycleStatus.COMPLETED) {
                completedCycles++;
            }
        }

        BigDecimal balance = totalContributions.add(totalFinesCollected).subtract(totalPayouts);

        return MerryGoRoundSummaryDto.builder()
                .totalCycles(allCycles.size())
                .activeCycles(activeCycles)
                .completedCycles(completedCycles)
                .totalContributions(totalContributions)
                .totalPayouts(totalPayouts)
                .totalFinesCollected(totalFinesCollected)
                .totalFinesPending(totalFinesPending)
                .currentBalance(balance)
                .activeCyclesList(activeCyclesList)
                .build();
    }

    // ==================== HELPER METHODS ====================

    private LocalDate calculateNextDate(LocalDate current, MerryGoRoundCycle cycle) {
        switch (cycle.getFrequency()) {
            case DAILY:
                return current.plusDays(1);
            case WEEKLY:
                if (cycle.getDayOfWeek() != null) {
                    DayOfWeek targetDay = DayOfWeek.of(cycle.getDayOfWeek());
                    return current.with(TemporalAdjusters.next(targetDay));
                }
                return current.plusWeeks(1);
            case MONTHLY:
                if (cycle.getDayOfMonth() != null) {
                    // Specific day of month
                    LocalDate nextMonth = current.plusMonths(1);
                    int day = Math.min(cycle.getDayOfMonth(), nextMonth.lengthOfMonth());
                    return nextMonth.withDayOfMonth(day);
                } else if (cycle.getWeekOfMonth() != null && cycle.getDayOfWeekInMonth() != null) {
                    // Nth weekday of month (e.g., first Monday)
                    LocalDate nextMonth = current.plusMonths(1).withDayOfMonth(1);
                    DayOfWeek targetDay = DayOfWeek.of(cycle.getDayOfWeekInMonth());
                    if (cycle.getWeekOfMonth() == -1) {
                        return nextMonth.with(TemporalAdjusters.lastInMonth(targetDay));
                    } else {
                        return nextMonth.with(TemporalAdjusters.dayOfWeekInMonth(cycle.getWeekOfMonth(), targetDay));
                    }
                }
                return current.plusMonths(1);
            default:
                return current.plusMonths(1);
        }
    }

    private MerryGoRoundCycleDto toCycleDto(MerryGoRoundCycle cycle) {
        int participantCount = participantRepository.countActiveParticipants(cycle.getId());
        List<MerryGoRoundRound> rounds = roundRepository.findByCycleIdOrderByPosition(cycle.getId());
        long completedRounds = rounds.stream()
                .filter(r -> r.getStatus() == MerryGoRoundRound.RoundStatus.COMPLETED)
                .count();

        BigDecimal totalContributed = contributionRepository.sumPaidContributions(cycle.getId());
        BigDecimal totalFinesPaid = fineRepository.sumPaidFines(cycle.getId());
        BigDecimal totalFinesPending = fineRepository.sumPendingFines(cycle.getId());

        // Sum actual paid amounts from rounds
        BigDecimal totalPaidOut = rounds.stream()
                .map(MerryGoRoundRound::getPaidAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Find current round (first pending or partial)
        MerryGoRoundRoundDto currentRound = rounds.stream()
                .filter(r -> r.getStatus() == MerryGoRoundRound.RoundStatus.PENDING 
                        || r.getStatus() == MerryGoRoundRound.RoundStatus.PARTIAL)
                .findFirst()
                .map(this::toRoundDto)
                .orElse(null);

        return MerryGoRoundCycleDto.builder()
                .id(cycle.getId())
                .name(cycle.getName())
                .contributionAmount(cycle.getContributionAmount())
                .startDate(cycle.getStartDate().toString())
                .endDate(cycle.getEndDate() != null ? cycle.getEndDate().toString() : null)
                .status(cycle.getStatus().name())
                .frequency(cycle.getFrequency().name())
                .dayOfWeek(cycle.getDayOfWeek())
                .dayOfMonth(cycle.getDayOfMonth())
                .weekOfMonth(cycle.getWeekOfMonth())
                .dayOfWeekInMonth(cycle.getDayOfWeekInMonth())
                .latePaymentFineAmount(cycle.getLatePaymentFineAmount())
                .autoImposeFine(cycle.getAutoImposeFine())
                .gracePeriodDays(cycle.getGracePeriodDays())
                .isRecurring(cycle.getIsRecurring())
                .numberOfCycles(cycle.getNumberOfCycles())
                .currentCycleNumber(cycle.getCurrentCycleNumber())
                .participantCount(participantCount)
                .completedRounds((int) completedRounds)
                .totalRounds(rounds.size())
                .totalContributed(totalContributed != null ? totalContributed : BigDecimal.ZERO)
                .totalPaidOut(totalPaidOut)
                .totalFinesCollected(totalFinesPaid != null ? totalFinesPaid : BigDecimal.ZERO)
                .totalFinesPending(totalFinesPending != null ? totalFinesPending : BigDecimal.ZERO)
                .currentRound(currentRound)
                .build();
    }

    private MerryGoRoundCycleDto toCycleDetailDto(MerryGoRoundCycle cycle) {
        MerryGoRoundCycleDto dto = toCycleDto(cycle);
        dto.setParticipants(participantRepository.findByCycleIdOrderByPayoutOrder(cycle.getId()).stream()
                .map(this::toParticipantDto)
                .collect(Collectors.toList()));
        dto.setRounds(roundRepository.findByCycleIdOrderByPosition(cycle.getId()).stream()
                .map(this::toRoundDto)
                .collect(Collectors.toList()));
        return dto;
    }

    private MerryGoRoundRoundDto toRoundDto(MerryGoRoundRound round) {
        int paidCount = contributionRepository.countPaidForRound(round.getId());
        int totalCount = contributionRepository.countTotalForRound(round.getId());

        return MerryGoRoundRoundDto.builder()
                .id(round.getId())
                .cycleId(round.getCycle().getId())
                .cycleName(round.getCycle().getName())
                .recipientId(round.getRecipient().getId())
                .recipientName(round.getRecipient().getName())
                .payoutDate(round.getPayoutDate().toString())
                .expectedAmount(round.getExpectedAmount())
                .collectedAmount(round.getCollectedAmount())
                .paidAmount(round.getPaidAmount())
                .payoutBalance(round.getPayoutBalance())
                .status(round.getStatus().name())
                .position(round.getPosition())
                .paidContributions(paidCount)
                .totalContributions(totalCount)
                .build();
    }

    private MerryGoRoundParticipantDto toParticipantDto(MerryGoRoundParticipant participant) {
        List<MerryGoRoundContribution> contributions = contributionRepository
                .findByMemberIdAndCycleId(participant.getMember().getId(), participant.getCycle().getId());
        
        BigDecimal totalContributed = contributions.stream()
                .filter(c -> c.getStatus() == MerryGoRoundContribution.ContributionStatus.PAID)
                .map(MerryGoRoundContribution::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalOwed = contributions.stream()
                .filter(c -> c.getStatus() == MerryGoRoundContribution.ContributionStatus.PENDING)
                .map(MerryGoRoundContribution::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalFines = fineRepository.sumPendingFinesForMember(
                participant.getMember().getId(), participant.getCycle().getId());

        // Check if received payout
        boolean hasReceived = participant.getStatus() == MerryGoRoundParticipant.ParticipantStatus.RECEIVED_PAYOUT;
        String payoutDate = null;
        if (hasReceived) {
            roundRepository.findByCycleIdOrderByPosition(participant.getCycle().getId()).stream()
                    .filter(r -> r.getRecipient().getId().equals(participant.getMember().getId()) 
                            && r.getStatus() == MerryGoRoundRound.RoundStatus.PAID)
                    .findFirst()
                    .ifPresent(r -> {});
        }

        return MerryGoRoundParticipantDto.builder()
                .id(participant.getId())
                .memberId(participant.getMember().getId())
                .memberName(participant.getMember().getName())
                .payoutOrder(participant.getPayoutOrder())
                .status(participant.getStatus().name())
                .totalContributed(totalContributed)
                .totalOwed(totalOwed)
                .totalFines(totalFines != null ? totalFines : BigDecimal.ZERO)
                .hasReceivedPayout(hasReceived)
                .build();
    }

    private MerryGoRoundContributionDto toContributionDto(MerryGoRoundContribution contribution) {
        // Check if a fine exists for this contribution
        boolean hasFine = fineRepository.existsByContributionId(contribution.getId());
        
        // For single contribution lookup, do individual query
        boolean hasPendingClaim = false;
        Long payableId = null;
        if (contribution.getStatus() != MerryGoRoundContribution.ContributionStatus.PAID) {
            var payableOpt = payableRepository.findByPayableTypeAndPayableRefId(PayableType.MGR_CONTRIBUTION, contribution.getId());
            if (payableOpt.isPresent()) {
                var payable = payableOpt.get();
                hasPendingClaim = payable.getStatus() == PayableStatus.AWAITING_CONFIRMATION;
                if (hasPendingClaim) {
                    payableId = payable.getId();
                }
            }
        }
        
        return toContributionDto(contribution, hasFine, hasPendingClaim, payableId);
    }
    
    private MerryGoRoundContributionDto toContributionDto(MerryGoRoundContribution contribution, boolean hasFine, boolean hasPendingClaim) {
        return toContributionDto(contribution, hasFine, hasPendingClaim, null);
    }
    
    private MerryGoRoundContributionDto toContributionDto(MerryGoRoundContribution contribution, boolean hasFine, boolean hasPendingClaim, Long payableId) {
        // Get round info for better display
        MerryGoRoundRound round = contribution.getRound();
        
        return MerryGoRoundContributionDto.builder()
                .id(contribution.getId())
                .cycleId(contribution.getCycle().getId())
                .roundId(round.getId())
                .roundPosition(round.getPosition())
                .roundRecipientName(round.getRecipient().getName())
                .memberId(contribution.getMember().getId())
                .memberName(contribution.getMember().getName())
                .amount(contribution.getAmount())
                .dueDate(contribution.getDueDate().toString())
                .paidDate(contribution.getPaidDate() != null ? contribution.getPaidDate().toString() : null)
                .status(contribution.getStatus().name())
                .paymentReference(contribution.getPaymentReference())
                .isOverdue(contribution.isOverdue())
                .hasFine(hasFine)
                .hasPendingClaim(hasPendingClaim)
                .payableId(payableId)
                .build();
    }
    
    /**
     * Convert list of contributions to DTOs with batch pending claim lookup.
     */
    private List<MerryGoRoundContributionDto> toContributionDtoListWithPendingClaims(List<MerryGoRoundContribution> contributions, Long tenantId) {
        if (contributions.isEmpty()) return List.of();
        
        // Batch fetch which contributions have fines
        Set<Long> contributionIdsWithFines = contributions.stream()
                .map(MerryGoRoundContribution::getId)
                .filter(fineRepository::existsByContributionId)
                .collect(java.util.stream.Collectors.toSet());
        
        // Get IDs of unpaid contributions
        List<Long> unpaidIds = contributions.stream()
                .filter(c -> c.getStatus() != MerryGoRoundContribution.ContributionStatus.PAID)
                .map(MerryGoRoundContribution::getId)
                .collect(Collectors.toList());
        
        // Batch fetch payables with pending claims - get full entities to extract payableId
        Map<Long, Long> pendingClaimPayableIds = new HashMap<>();
        if (!unpaidIds.isEmpty()) {
            List<Payable> pendingPayables = payableRepository.findByTenant_IdAndPayableTypeAndPayableRefIdInAndStatus(
                    tenantId, PayableType.MGR_CONTRIBUTION, unpaidIds, PayableStatus.AWAITING_CONFIRMATION);
            for (Payable p : pendingPayables) {
                pendingClaimPayableIds.put(p.getPayableRefId(), p.getId());
            }
        }
        
        List<MerryGoRoundContributionDto> result = new ArrayList<>();
        for (MerryGoRoundContribution c : contributions) {
            boolean hasFine = contributionIdsWithFines.contains(c.getId());
            boolean hasPendingClaim = pendingClaimPayableIds.containsKey(c.getId());
            Long payableId = pendingClaimPayableIds.get(c.getId());
            result.add(toContributionDto(c, hasFine, hasPendingClaim, payableId));
        }
        return result;
    }

    private MerryGoRoundFineDto toFineDto(MerryGoRoundFine fine) {
        return MerryGoRoundFineDto.builder()
                .id(fine.getId())
                .cycleId(fine.getCycle().getId())
                .memberId(fine.getMember().getId())
                .memberName(fine.getMember().getName())
                .contributionId(fine.getContribution() != null ? fine.getContribution().getId() : null)
                .reason(fine.getReason())
                .amount(fine.getAmount())
                .imposedDate(fine.getImposedDate().toString())
                .paidDate(fine.getPaidDate() != null ? fine.getPaidDate().toString() : null)
                .status(fine.getStatus().name())
                .autoImposed(fine.getAutoImposed())
                .waivedBy(fine.getWaivedBy())
                .waiveReason(fine.getWaiveReason())
                .hasPendingClaim(false) // Will be updated in batch lookup
                .payableId(null) // Will be updated in batch lookup
                .build();
    }

    /**
     * Convert fines to DTOs with batch pending claim lookup.
     */
    private List<MerryGoRoundFineDto> toFineDtoListWithPendingClaims(List<MerryGoRoundFine> fines, Long tenantId) {
        if (fines.isEmpty()) return List.of();
        
        // Get IDs of pending fines
        List<Long> pendingIds = fines.stream()
                .filter(f -> f.getStatus() == MerryGoRoundFine.FineStatus.PENDING)
                .map(MerryGoRoundFine::getId)
                .collect(Collectors.toList());
        
        // Batch fetch payables with pending claims
        Map<Long, Long> pendingClaimPayableIds = new HashMap<>();
        if (!pendingIds.isEmpty()) {
            List<Payable> payables = payableRepository.findByTenant_IdAndPayableTypeAndPayableRefIdInAndStatus(
                    tenantId, PayableType.MGR_FINE, pendingIds, PayableStatus.AWAITING_CONFIRMATION);
            for (Payable p : payables) {
                pendingClaimPayableIds.put(p.getPayableRefId(), p.getId());
            }
        }
        
        return fines.stream()
                .map(fine -> {
                    boolean hasPendingClaim = pendingClaimPayableIds.containsKey(fine.getId());
                    Long payableId = pendingClaimPayableIds.get(fine.getId());
                    
                    return MerryGoRoundFineDto.builder()
                            .id(fine.getId())
                            .cycleId(fine.getCycle().getId())
                            .memberId(fine.getMember().getId())
                            .memberName(fine.getMember().getName())
                            .contributionId(fine.getContribution() != null ? fine.getContribution().getId() : null)
                            .reason(fine.getReason())
                            .amount(fine.getAmount())
                            .imposedDate(fine.getImposedDate().toString())
                            .paidDate(fine.getPaidDate() != null ? fine.getPaidDate().toString() : null)
                            .status(fine.getStatus().name())
                            .autoImposed(fine.getAutoImposed())
                            .waivedBy(fine.getWaivedBy())
                            .waiveReason(fine.getWaiveReason())
                            .hasPendingClaim(hasPendingClaim)
                            .payableId(payableId)
                            .build();
                })
                .collect(Collectors.toList());
    }
}
