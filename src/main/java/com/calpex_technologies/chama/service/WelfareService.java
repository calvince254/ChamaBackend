package com.calpex_technologies.chama.service;

import com.calpex_technologies.chama.dto.WelfareContributionDto;
import com.calpex_technologies.chama.dto.WelfareEventDto;
import com.calpex_technologies.chama.entity.*;
import com.calpex_technologies.chama.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class WelfareService {

    private final WelfareEventRepository welfareEventRepository;
    private final WelfareContributionRepository welfareContributionRepository;
    private final MemberRepository memberRepository;
    private final TenantRepository tenantRepository;
    private final ContributionRepository contributionRepository;
    private final ContributionTypeRepository contributionTypeRepository;
    private final TransactionRepository transactionRepository;

    public List<WelfareEventDto> getAllWelfareEvents(Long tenantId) {
        return welfareEventRepository.findByTenantIdOrderByDateReportedDesc(tenantId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public Page<WelfareEventDto> getWelfareEventsPaginated(Long tenantId, int page, int size, String status) {
        Pageable pageable = PageRequest.of(page, size);
        Page<WelfareEvent> eventPage;
        
        if (status != null && !status.isEmpty()) {
            WelfareEvent.WelfareStatus welfareStatus = WelfareEvent.WelfareStatus.valueOf(status);
            eventPage = welfareEventRepository.findByTenantIdAndStatus(tenantId, welfareStatus, pageable);
        } else {
            eventPage = welfareEventRepository.findByTenantIdOrderByDateReportedDesc(tenantId, pageable);
        }
        
        return eventPage.map(this::toDto);
    }

    public WelfareEventDto getWelfareEvent(Long id, Long tenantId) {
        WelfareEvent event = welfareEventRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Welfare event not found"));
        return toDto(event);
    }

    @Transactional
    public WelfareEventDto createWelfareEvent(WelfareEventDto dto, Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        Member member = memberRepository.findByIdAndTenantId(dto.getMemberId(), tenantId)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        // Determine which members are involved
        List<Member> involvedMembers;
        boolean allMembersInvolved = dto.getAllMembersInvolved() == null || dto.getAllMembersInvolved();
        
        if (allMembersInvolved) {
            involvedMembers = memberRepository.findActiveByTenantId(tenantId);
        } else if (dto.getInvolvedMemberIds() != null && !dto.getInvolvedMemberIds().isEmpty()) {
            involvedMembers = memberRepository.findAllById(dto.getInvolvedMemberIds()).stream()
                    .filter(m -> m.getTenant().getId().equals(tenantId))
                    .collect(Collectors.toList());
        } else {
            // Default to all active members if no specific selection
            involvedMembers = memberRepository.findActiveByTenantId(tenantId);
            allMembersInvolved = true;
        }

        BigDecimal totalExpected = dto.getAmountPerMember().multiply(BigDecimal.valueOf(involvedMembers.size()));

        WelfareEvent event = WelfareEvent.builder()
                .member(member)
                .eventType(WelfareEvent.EventType.valueOf(dto.getEventType()))
                .relation(dto.getRelation())
                .dateReported(LocalDate.now())
                .amountPerMember(dto.getAmountPerMember())
                .totalExpected(totalExpected)
                .totalCollected(BigDecimal.ZERO)
                .status(WelfareEvent.WelfareStatus.OPEN)
                .description(dto.getDescription())
                .allMembersInvolved(allMembersInvolved)
                .tenant(tenant)
                .build();

        event = welfareEventRepository.save(event);

        // Create contribution records for all involved members
        WelfareEvent savedEvent = event;
        for (Member involvedMember : involvedMembers) {
            WelfareContribution contribution = WelfareContribution.builder()
                    .welfareEvent(savedEvent)
                    .member(involvedMember)
                    .amountExpected(dto.getAmountPerMember())
                    .amountPaid(BigDecimal.ZERO)
                    .paid(false)
                    .paidFromContribution(false)
                    .paymentMethod(WelfareContribution.PaymentMethod.CASH)
                    .tenant(tenant)
                    .build();
            welfareContributionRepository.save(contribution);
        }

        return toDto(event);
    }

    @Transactional
    public WelfareEventDto updateWelfareEvent(Long id, WelfareEventDto dto, Long tenantId) {
        WelfareEvent event = welfareEventRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Welfare event not found"));

        event.setEventType(WelfareEvent.EventType.valueOf(dto.getEventType()));
        event.setRelation(dto.getRelation());
        event.setAmountPerMember(dto.getAmountPerMember());
        event.setTotalCollected(dto.getTotalCollected());
        event.setStatus(WelfareEvent.WelfareStatus.valueOf(dto.getStatus()));
        event.setDescription(dto.getDescription());

        event = welfareEventRepository.save(event);
        return toDto(event);
    }

    @Transactional
    public WelfareEventDto closeEvent(Long id, Long tenantId) {
        WelfareEvent event = welfareEventRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Welfare event not found"));
        event.setStatus(WelfareEvent.WelfareStatus.CLOSED);
        event = welfareEventRepository.save(event);
        return toDto(event);
    }

    // ==================== CONTRIBUTION MANAGEMENT ====================

    public List<WelfareContributionDto> getEventContributions(Long eventId, Long tenantId) {
        // Verify event exists and belongs to tenant
        welfareEventRepository.findByIdAndTenantId(eventId, tenantId)
                .orElseThrow(() -> new RuntimeException("Welfare event not found"));

        return welfareContributionRepository.findByWelfareEventIdAndTenantId(eventId, tenantId).stream()
                .map(this::toContributionDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public WelfareContributionDto updateContributionPayment(Long eventId, Long memberId, Boolean paid, Long tenantId) {
        WelfareEvent event = welfareEventRepository.findByIdAndTenantId(eventId, tenantId)
                .orElseThrow(() -> new RuntimeException("Welfare event not found"));

        WelfareContribution contribution = welfareContributionRepository
                .findByWelfareEventIdAndMemberIdAndTenantId(eventId, memberId, tenantId)
                .orElseThrow(() -> new RuntimeException("Contribution not found"));

        // Update paid status
        contribution.setPaid(paid);
        contribution.setPaymentMethod(WelfareContribution.PaymentMethod.CASH);
        contribution.setPaidFromContribution(false);
        if (paid) {
            contribution.setAmountPaid(contribution.getAmountExpected());
            contribution.setPaidDate(LocalDateTime.now());
        } else {
            contribution.setAmountPaid(BigDecimal.ZERO);
            contribution.setPaidDate(null);
        }

        contribution = welfareContributionRepository.save(contribution);

        // Recalculate total collected
        recalculateTotalCollected(event, tenantId);

        return toContributionDto(contribution);
    }

    /**
     * Pay welfare contribution using funds from a contribution pool
     */
    @Transactional
    public WelfareContributionDto payFromContributionFund(Long eventId, Long memberId, Long contributionTypeId, Long tenantId) {
        WelfareEvent event = welfareEventRepository.findByIdAndTenantId(eventId, tenantId)
                .orElseThrow(() -> new RuntimeException("Welfare event not found"));

        WelfareContribution welfareContribution = welfareContributionRepository
                .findByWelfareEventIdAndMemberIdAndTenantId(eventId, memberId, tenantId)
                .orElseThrow(() -> new RuntimeException("Welfare contribution not found"));

        if (welfareContribution.getPaid()) {
            throw new RuntimeException("Welfare contribution is already paid");
        }

        ContributionType contributionType = contributionTypeRepository.findByIdAndTenantId(contributionTypeId, tenantId)
                .orElseThrow(() -> new RuntimeException("Contribution type not found"));

        // Get member's available balance in this contribution type
        BigDecimal availableBalance = getMemberContributionBalance(memberId, contributionTypeId, tenantId);
        BigDecimal amountNeeded = welfareContribution.getAmountExpected();

        if (availableBalance.compareTo(amountNeeded) < 0) {
            throw new RuntimeException("Insufficient balance in " + contributionType.getName() + 
                    ". Available: " + availableBalance + ", Required: " + amountNeeded);
        }

        // Find the most recent contribution to deduct from
        Contribution sourceContribution = findContributionToDeduct(memberId, contributionTypeId, amountNeeded, tenantId);
        if (sourceContribution == null) {
            throw new RuntimeException("No contribution record found to deduct from");
        }

        // Deduct from the contribution
        BigDecimal currentPaid = sourceContribution.getAmountPaid() != null 
                ? sourceContribution.getAmountPaid() : BigDecimal.ZERO;
        sourceContribution.setAmountPaid(currentPaid.subtract(amountNeeded));
        updateContributionStatus(sourceContribution);
        contributionRepository.save(sourceContribution);

        // Mark welfare contribution as paid
        welfareContribution.setPaid(true);
        welfareContribution.setAmountPaid(amountNeeded);
        welfareContribution.setPaidDate(LocalDateTime.now());
        welfareContribution.setPaidFromContribution(true);
        welfareContribution.setSourceContributionType(contributionType);
        welfareContribution.setSourceContribution(sourceContribution);
        welfareContribution.setPaymentMethod(WelfareContribution.PaymentMethod.CONTRIBUTION_FUND);
        welfareContribution = welfareContributionRepository.save(welfareContribution);

        // Recalculate welfare event totals
        recalculateTotalCollected(event, tenantId);

        // Create transaction records
        createWelfareFromContributionTransactions(welfareContribution, sourceContribution, event);

        log.info("Welfare payment from contribution fund: member={}, event={}, amount={}, source={}",
                welfareContribution.getMember().getName(), event.getId(), amountNeeded, contributionType.getName());

        return toContributionDto(welfareContribution);
    }

    /**
     * Get member's total paid balance in a contribution type
     */
    public BigDecimal getMemberContributionBalance(Long memberId, Long contributionTypeId, Long tenantId) {
        List<Contribution> contributions = contributionRepository
                .findByMemberIdAndContributionTypeIdAndTenantId(memberId, contributionTypeId, tenantId);
        
        return contributions.stream()
                .map(c -> c.getAmountPaid() != null ? c.getAmountPaid() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Get available contribution types with balances for a member
     */
    public List<ContributionBalanceDto> getMemberContributionBalances(Long memberId, Long tenantId) {
        Member member = memberRepository.findByIdAndTenantId(memberId, tenantId)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        List<ContributionType> types = contributionTypeRepository.findByTenantIdAndActiveTrue(tenantId);
        
        return types.stream()
                .map(type -> {
                    BigDecimal balance = getMemberContributionBalance(memberId, type.getId(), tenantId);
                    return new ContributionBalanceDto(
                            type.getId(),
                            type.getName(),
                            balance
                    );
                })
                .filter(dto -> dto.getBalance().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toList());
    }

    private Contribution findContributionToDeduct(Long memberId, Long contributionTypeId, BigDecimal amount, Long tenantId) {
        // Find contributions with available balance, ordered by month descending (most recent first)
        List<Contribution> contributions = contributionRepository
                .findByMemberIdAndContributionTypeIdAndTenantId(memberId, contributionTypeId, tenantId);
        
        // Sort by month descending
        contributions.sort((a, b) -> b.getMonth().compareTo(a.getMonth()));
        
        // Find one with sufficient balance
        for (Contribution c : contributions) {
            BigDecimal paid = c.getAmountPaid() != null ? c.getAmountPaid() : BigDecimal.ZERO;
            if (paid.compareTo(amount) >= 0) {
                return c;
            }
        }
        
        // If no single contribution has enough, use the one with highest balance
        return contributions.stream()
                .filter(c -> c.getAmountPaid() != null && c.getAmountPaid().compareTo(BigDecimal.ZERO) > 0)
                .findFirst()
                .orElse(null);
    }

    private void updateContributionStatus(Contribution contribution) {
        BigDecimal paid = contribution.getAmountPaid() != null ? contribution.getAmountPaid() : BigDecimal.ZERO;
        BigDecimal due = contribution.getAmountDue() != null ? contribution.getAmountDue() : BigDecimal.ZERO;
        
        if (paid.compareTo(due) >= 0) {
            contribution.setStatus(Contribution.ContributionStatus.PAID);
        } else if (paid.compareTo(BigDecimal.ZERO) > 0) {
            contribution.setStatus(Contribution.ContributionStatus.PARTIAL);
        } else if (contribution.getDueDate() != null && LocalDate.now().isAfter(contribution.getDueDate())) {
            contribution.setStatus(Contribution.ContributionStatus.OVERDUE);
        } else {
            contribution.setStatus(Contribution.ContributionStatus.PENDING);
        }
    }

    private void createWelfareFromContributionTransactions(WelfareContribution welfareContribution, 
                                                            Contribution sourceContribution, 
                                                            WelfareEvent event) {
        Tenant tenant = welfareContribution.getTenant();
        Member member = welfareContribution.getMember();
        BigDecimal amount = welfareContribution.getAmountPaid();

        // Transaction 1: OUT from contribution pool
        Transaction outTransaction = Transaction.builder()
                .date(LocalDate.now())
                .type(Transaction.TransactionType.WELFARE)
                .description("Welfare payment from " + sourceContribution.getContributionType().getName() + 
                        " for " + event.getEventType() + " - " + event.getMember().getName())
                .amount(amount)
                .direction(Transaction.TransactionDirection.OUT)
                .member(member)
                .referenceId(welfareContribution.getId())
                .referenceType("WELFARE_FROM_CONTRIBUTION")
                .tenant(tenant)
                .build();
        transactionRepository.save(outTransaction);

        // Transaction 2: IN to welfare fund
        Transaction inTransaction = Transaction.builder()
                .date(LocalDate.now())
                .type(Transaction.TransactionType.WELFARE)
                .description("Welfare contribution from " + member.getName() + " (from contribution fund)")
                .amount(amount)
                .direction(Transaction.TransactionDirection.IN)
                .member(member)
                .referenceId(event.getId())
                .referenceType("WELFARE_EVENT")
                .tenant(tenant)
                .build();
        transactionRepository.save(inTransaction);
    }

    @Transactional
    public void markAllAsPaid(Long eventId, Long tenantId) {
        WelfareEvent event = welfareEventRepository.findByIdAndTenantId(eventId, tenantId)
                .orElseThrow(() -> new RuntimeException("Welfare event not found"));

        List<WelfareContribution> contributions = welfareContributionRepository
                .findByWelfareEventIdAndTenantId(eventId, tenantId);

        LocalDateTime now = LocalDateTime.now();
        for (WelfareContribution c : contributions) {
            if (!c.getPaid()) {
                c.setPaid(true);
                c.setAmountPaid(c.getAmountExpected());
                c.setPaidDate(now);
                c.setPaymentMethod(WelfareContribution.PaymentMethod.CASH);
                welfareContributionRepository.save(c);
            }
        }

        recalculateTotalCollected(event, tenantId);
    }

    private void recalculateTotalCollected(WelfareEvent event, Long tenantId) {
        List<WelfareContribution> contributions = welfareContributionRepository
                .findByWelfareEventIdAndTenantId(event.getId(), tenantId);

        BigDecimal totalCollected = contributions.stream()
                .map(WelfareContribution::getAmountPaid)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        event.setTotalCollected(totalCollected);
        welfareEventRepository.save(event);
    }

    private WelfareEventDto toDto(WelfareEvent event) {
        // Get contribution stats
        List<WelfareContribution> contributions = welfareContributionRepository
                .findByWelfareEventIdAndTenantId(event.getId(), event.getTenant().getId());
        
        int totalInvolved = contributions.size();
        int paidCount = (int) contributions.stream().filter(WelfareContribution::getPaid).count();

        return WelfareEventDto.builder()
                .id(event.getId())
                .memberId(event.getMember().getId())
                .memberName(event.getMember().getName())
                .eventType(event.getEventType().name())
                .relation(event.getRelation())
                .dateReported(event.getDateReported().toString())
                .amountPerMember(event.getAmountPerMember())
                .totalExpected(event.getTotalExpected())
                .totalCollected(event.getTotalCollected())
                .status(event.getStatus().name())
                .description(event.getDescription())
                .allMembersInvolved(event.getAllMembersInvolved())
                .totalInvolved(totalInvolved)
                .paidCount(paidCount)
                .build();
    }

    private WelfareContributionDto toContributionDto(WelfareContribution contribution) {
        WelfareContributionDto.WelfareContributionDtoBuilder builder = WelfareContributionDto.builder()
                .id(contribution.getId())
                .welfareEventId(contribution.getWelfareEvent().getId())
                .memberId(contribution.getMember().getId())
                .memberName(contribution.getMember().getName())
                .memberNo(contribution.getMember().getMemberNo())
                .amountExpected(contribution.getAmountExpected())
                .amountPaid(contribution.getAmountPaid())
                .paid(contribution.getPaid())
                .paidDate(contribution.getPaidDate() != null ? contribution.getPaidDate().toString() : null)
                .paymentReference(contribution.getPaymentReference())
                .paidFromContribution(contribution.getPaidFromContribution())
                .paymentMethod(contribution.getPaymentMethod() != null ? contribution.getPaymentMethod().name() : null);

        if (contribution.getSourceContributionType() != null) {
            builder.sourceContributionTypeId(contribution.getSourceContributionType().getId())
                   .sourceContributionTypeName(contribution.getSourceContributionType().getName());
        }
        if (contribution.getSourceContribution() != null) {
            builder.sourceContributionId(contribution.getSourceContribution().getId());
        }

        return builder.build();
    }

    // Inner DTO for contribution balances
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class ContributionBalanceDto {
        private Long contributionTypeId;
        private String contributionTypeName;
        private BigDecimal balance;
    }
}
