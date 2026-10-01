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
import java.util.Map;
import java.util.Set;
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
    private final PayableRepository payableRepository;
    private final PaymentClaimRepository paymentClaimRepository;

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
        // Validate required fields
        if (dto.getAmountPerMember() == null) {
            throw new RuntimeException("Amount per member is required");
        }
        if (dto.getMemberId() == null) {
            throw new RuntimeException("Beneficiary member is required");
        }
        if (dto.getEventType() == null || dto.getEventType().isEmpty()) {
            throw new RuntimeException("Event type is required");
        }
        
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

        // Set funding contribution types if provided
        if (dto.getFundingContributionTypeIds() != null && !dto.getFundingContributionTypeIds().isEmpty()) {
            Set<ContributionType> fundingTypes = dto.getFundingContributionTypeIds().stream()
                    .map(typeId -> contributionTypeRepository.findByIdAndTenantId(typeId, tenantId)
                            .orElse(null))
                    .filter(ct -> ct != null)
                    .collect(Collectors.toSet());
            event.setFundingContributionTypes(fundingTypes);
        }

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

        List<WelfareContribution> contributions = welfareContributionRepository.findByWelfareEventIdAndTenantId(eventId, tenantId);
        return toContributionDtoListWithPendingClaims(contributions, tenantId);
    }

    public Page<WelfareContributionDto> getEventContributionsPaginated(Long eventId, Long tenantId, int page, int size) {
        // Verify event exists and belongs to tenant
        welfareEventRepository.findByIdAndTenantId(eventId, tenantId)
                .orElseThrow(() -> new RuntimeException("Welfare event not found"));

        Pageable pageable = PageRequest.of(page, size);
        Page<WelfareContribution> contributions = welfareContributionRepository.findByWelfareEventIdAndTenantId(eventId, tenantId, pageable);
        
        // Get pending claims for all contributions in this page
        List<Long> contributionIds = contributions.getContent().stream()
                .map(WelfareContribution::getId)
                .collect(Collectors.toList());
        
        // Find payables for these contributions that are awaiting confirmation
        List<Payable> payables = payableRepository
                .findByTenant_IdAndPayableTypeAndPayableRefIdInAndStatus(
                        tenantId,
                        PayableType.WELFARE_DUE,
                        contributionIds,
                        PayableStatus.AWAITING_CONFIRMATION);
        
        Set<Long> contributionsWithPendingClaims = payables.stream()
                .map(Payable::getPayableRefId)
                .collect(Collectors.toSet());
        
        Map<Long, Long> payableIdMap = payables.stream()
                .collect(Collectors.toMap(Payable::getPayableRefId, Payable::getId, (a, b) -> a));
        
        return contributions.map(c -> {
            boolean hasPendingClaim = contributionsWithPendingClaims.contains(c.getId());
            Long payableId = payableIdMap.get(c.getId());
            return toContributionDto(c, hasPendingClaim, payableId);
        });
    }

    @Transactional
    public WelfareContributionDto updateContributionPayment(Long eventId, Long memberId, Boolean paid, Long tenantId) {
        WelfareEvent event = welfareEventRepository.findByIdAndTenantId(eventId, tenantId)
                .orElseThrow(() -> new RuntimeException("Welfare event not found"));

        WelfareContribution contribution = welfareContributionRepository
                .findByWelfareEventIdAndMemberIdAndTenantId(eventId, memberId, tenantId)
                .orElseThrow(() -> new RuntimeException("Contribution not found"));

        // Check if there's already a fund payment (partial payment from fund)
        BigDecimal existingFromFund = contribution.getAmountFromFund() != null ? contribution.getAmountFromFund() : BigDecimal.ZERO;
        
        // Update paid status
        contribution.setPaid(paid);
        
        if (paid) {
            contribution.setAmountPaid(contribution.getAmountExpected());
            contribution.setPaidDate(LocalDateTime.now());
            
            if (existingFromFund.compareTo(BigDecimal.ZERO) > 0) {
                // There was a prior fund payment - this is completing the rest via cash (MIXED)
                BigDecimal cashPortion = contribution.getAmountExpected().subtract(existingFromFund);
                contribution.setAmountFromCash(cashPortion);
                contribution.setPaymentMethod(WelfareContribution.PaymentMethod.MIXED);
                contribution.setPaidFromContribution(true); // Keep true since fund was used
            } else {
                // Pure cash payment
                contribution.setAmountFromCash(contribution.getAmountExpected());
                contribution.setAmountFromFund(BigDecimal.ZERO);
                contribution.setPaymentMethod(WelfareContribution.PaymentMethod.CASH);
                contribution.setPaidFromContribution(false);
            }
        } else {
            contribution.setAmountPaid(BigDecimal.ZERO);
            contribution.setAmountFromCash(BigDecimal.ZERO);
            contribution.setAmountFromFund(BigDecimal.ZERO);
            contribution.setPaidDate(null);
            contribution.setPaymentMethod(WelfareContribution.PaymentMethod.CASH);
            contribution.setPaidFromContribution(false);
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
    public WelfareContributionDto payFromContributionFund(Long eventId, Long memberId, Long contributionTypeId, BigDecimal amountFromFund, Long tenantId) {
        log.info("=== PAY FROM CONTRIBUTION FUND START ===");
        log.info("Parameters: eventId={}, memberId={}, contributionTypeId={}, amountFromFund={}, tenantId={}", 
                eventId, memberId, contributionTypeId, amountFromFund, tenantId);
        
        WelfareEvent event = welfareEventRepository.findByIdAndTenantId(eventId, tenantId)
                .orElseThrow(() -> new RuntimeException("Welfare event not found"));
        log.info("Found welfare event: id={}, type={}, beneficiary={}", event.getId(), event.getEventType(), event.getMember().getName());

        WelfareContribution welfareContribution = welfareContributionRepository
                .findByWelfareEventIdAndMemberIdAndTenantId(eventId, memberId, tenantId)
                .orElseThrow(() -> new RuntimeException("Welfare contribution not found"));
        log.info("Found welfare contribution: id={}, member={}, amountExpected={}, currentPaid={}", 
                welfareContribution.getId(), welfareContribution.getMember().getName(), 
                welfareContribution.getAmountExpected(), welfareContribution.getAmountPaid());

        if (welfareContribution.getPaid()) {
            throw new RuntimeException("Welfare contribution is already paid");
        }

        ContributionType contributionType = contributionTypeRepository.findByIdAndTenantId(contributionTypeId, tenantId)
                .orElseThrow(() -> new RuntimeException("Contribution type not found"));
        log.info("Found contribution type: id={}, name={}", contributionType.getId(), contributionType.getName());

        // Get member's available balance in this contribution type (Total Paid - Previous Welfare Deductions)
        BigDecimal availableBalance = getMemberContributionBalance(memberId, contributionTypeId, tenantId);
        log.info("Member available balance in {}: {}", contributionType.getName(), availableBalance);
        
        BigDecimal amountNeeded = welfareContribution.getAmountExpected();
        
        // If no specific amount provided, use full amount needed
        BigDecimal amountToDeduct = amountFromFund != null ? amountFromFund : amountNeeded;
        log.info("Amount to deduct: {} (requested: {}, needed: {})", amountToDeduct, amountFromFund, amountNeeded);
        
        // Validate amount
        if (amountToDeduct.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Amount must be greater than zero");
        }
        if (amountToDeduct.compareTo(amountNeeded) > 0) {
            throw new RuntimeException("Amount from fund cannot exceed amount due");
        }
        if (amountToDeduct.compareTo(availableBalance) > 0) {
            throw new RuntimeException("Insufficient balance in " + contributionType.getName() + 
                    ". Available: " + availableBalance + ", Requested: " + amountToDeduct);
        }

        // NOTE: We deduct from the TOTAL balance in the contribution type, not from a specific contribution record.
        // The member may have Jan, Feb, Mar contributions totaling 300. We check that total, then create a debit transaction.
        // Individual contribution records are NOT modified - they keep their full paid amounts.
        // Available balance is calculated as: Total Paid - Sum of Welfare Deductions (tracked in welfare_contributions.amount_from_fund)

        // Update welfare contribution with partial or full payment
        BigDecimal previousPaid = welfareContribution.getAmountPaid() != null ? welfareContribution.getAmountPaid() : BigDecimal.ZERO;
        BigDecimal newTotalPaid = previousPaid.add(amountToDeduct);
        log.info("Welfare contribution payment: previousPaid={}, newTotalPaid={}", previousPaid, newTotalPaid);
        
        // Track amount from fund
        BigDecimal previousFromFund = welfareContribution.getAmountFromFund() != null ? welfareContribution.getAmountFromFund() : BigDecimal.ZERO;
        welfareContribution.setAmountFromFund(previousFromFund.add(amountToDeduct));
        log.info("Amount from fund: previous={}, new={}", previousFromFund, welfareContribution.getAmountFromFund());
        
        welfareContribution.setAmountPaid(newTotalPaid);
        welfareContribution.setPaidFromContribution(true);
        welfareContribution.setSourceContributionType(contributionType);
        log.info("Set sourceContributionType: id={}, name={}", contributionType.getId(), contributionType.getName());
        // Note: sourceContribution is left null since we're deducting from total, not a specific record
        
        // Mark as fully paid only if total paid equals expected
        if (newTotalPaid.compareTo(amountNeeded) >= 0) {
            welfareContribution.setPaid(true);
            welfareContribution.setPaidDate(LocalDateTime.now());
            // Check if it's mixed payment
            BigDecimal fromCash = welfareContribution.getAmountFromCash() != null ? welfareContribution.getAmountFromCash() : BigDecimal.ZERO;
            if (fromCash.compareTo(BigDecimal.ZERO) > 0) {
                welfareContribution.setPaymentMethod(WelfareContribution.PaymentMethod.MIXED);
                log.info("Payment method set to MIXED (fund: {}, cash: {})", welfareContribution.getAmountFromFund(), fromCash);
            } else {
                welfareContribution.setPaymentMethod(WelfareContribution.PaymentMethod.CONTRIBUTION_FUND);
                log.info("Payment method set to CONTRIBUTION_FUND");
            }
        }
        
        welfareContribution = welfareContributionRepository.save(welfareContribution);
        log.info("Saved welfare contribution: id={}, paid={}, paymentMethod={}", 
                welfareContribution.getId(), welfareContribution.getPaid(), welfareContribution.getPaymentMethod());

        // Recalculate welfare event totals
        recalculateTotalCollected(event, tenantId);

        // Create debit transaction showing the fund usage
        log.info("Creating debit transaction...");
        createWelfareDebitTransaction(welfareContribution, contributionType, event, amountToDeduct);

        log.info("=== PAY FROM CONTRIBUTION FUND COMPLETE ===");
        log.info("Welfare payment from contribution fund: member={}, event={}, amount={}, source={}, fullPayment={}",
                welfareContribution.getMember().getName(), event.getId(), amountToDeduct, contributionType.getName(), welfareContribution.getPaid());

        return toContributionDto(welfareContribution);
    }
    
    /**
     * Legacy method for full payment (backward compatibility)
     */
    @Transactional
    public WelfareContributionDto payFromContributionFund(Long eventId, Long memberId, Long contributionTypeId, Long tenantId) {
        return payFromContributionFund(eventId, memberId, contributionTypeId, null, tenantId);
    }

    /**
     * Get member's available balance in a contribution type.
     * Balance = Sum of Credits (IN) - Sum of Debits (OUT) for that contribution type.
     * This includes all contribution payments minus any welfare deductions.
     */
    public BigDecimal getMemberContributionBalance(Long memberId, Long contributionTypeId, Long tenantId) {
        log.info("=== CALCULATING MEMBER BALANCE ===");
        log.info("memberId={}, contributionTypeId={}, tenantId={}", memberId, contributionTypeId, tenantId);
        
        // Get total credits (contribution payments IN)
        BigDecimal totalCredits = transactionRepository
                .sumCreditsByMemberAndContributionType(memberId, contributionTypeId, tenantId);
        log.info("Total credits (IN): {}", totalCredits);
        
        // Get total debits (welfare deductions OUT)
        BigDecimal totalDebits = transactionRepository
                .sumDebitsByMemberAndContributionType(memberId, contributionTypeId, tenantId);
        log.info("Total debits (OUT): {}", totalDebits);
        
        BigDecimal balance = totalCredits.subtract(totalDebits);
        log.info("Available balance: {} - {} = {}", totalCredits, totalDebits, balance);
        
        return balance;
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

    /**
     * Create a debit transaction for welfare payment from contribution fund.
     * This is the ONLY transaction created - showing money going OUT from the member's contribution pool.
     */
    private void createWelfareDebitTransaction(WelfareContribution welfareContribution, 
                                                ContributionType contributionType, 
                                                WelfareEvent event,
                                                BigDecimal amount) {
        Tenant tenant = welfareContribution.getTenant();
        Member member = welfareContribution.getMember();

        log.info("=== CREATING WELFARE DEBIT TRANSACTION ===");
        log.info("Member: id={}, name={}", member.getId(), member.getName());
        log.info("Contribution Type: id={}, name={}", contributionType.getId(), contributionType.getName());
        log.info("Amount: {}", amount);
        log.info("Welfare Event: id={}, type={}, beneficiary={}", event.getId(), event.getEventType(), event.getMember().getName());

        // Create ONE debit (OUT) transaction from the member's contribution fund total
        // This transaction has contributionType set so balance can be calculated as credits - debits
        Transaction outTransaction = Transaction.builder()
                .date(LocalDate.now())
                .type(Transaction.TransactionType.WELFARE)
                .description("Welfare payment from " + contributionType.getName() + 
                        " for " + event.getEventType() + " - " + event.getMember().getName())
                .amount(amount)
                .direction(Transaction.TransactionDirection.OUT)
                .member(member)
                .referenceId(welfareContribution.getId())
                .referenceType("WELFARE_FROM_CONTRIBUTION")
                .contributionType(contributionType)  // Link to contribution type for balance calculation
                .tenant(tenant)
                .build();
        
        log.info("Transaction built: type={}, direction={}, amount={}, member={}, contributionType={}", 
                outTransaction.getType(), outTransaction.getDirection(), outTransaction.getAmount(), 
                outTransaction.getMember().getName(), outTransaction.getContributionType().getName());
        
        Transaction saved = transactionRepository.save(outTransaction);
        log.info("=== TRANSACTION SAVED: id={} ===", saved.getId());
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

        // Get funding sources
        List<Long> fundingTypeIds = event.getFundingContributionTypes() != null 
                ? event.getFundingContributionTypes().stream()
                        .map(ContributionType::getId)
                        .collect(Collectors.toList())
                : List.of();
        
        List<WelfareEventDto.FundingSourceDto> fundingSources = event.getFundingContributionTypes() != null
                ? event.getFundingContributionTypes().stream()
                        .map(ct -> WelfareEventDto.FundingSourceDto.builder()
                                .contributionTypeId(ct.getId())
                                .contributionTypeName(ct.getName())
                                .build())
                        .collect(Collectors.toList())
                : List.of();

        // Calculate disbursement amounts
        BigDecimal totalDisbursed = getAlreadyDisbursedAmount(event, event.getTenant().getId());
        BigDecimal totalCollected = event.getTotalCollected() != null ? event.getTotalCollected() : BigDecimal.ZERO;
        BigDecimal availableToDisbure = totalCollected.subtract(totalDisbursed);

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
                .totalDisbursed(totalDisbursed)
                .availableToDisbure(availableToDisbure)
                .status(event.getStatus().name())
                .description(event.getDescription())
                .allMembersInvolved(event.getAllMembersInvolved())
                .totalInvolved(totalInvolved)
                .paidCount(paidCount)
                .fundingContributionTypeIds(fundingTypeIds)
                .fundingSources(fundingSources)
                .build();
    }

    private WelfareContributionDto toContributionDto(WelfareContribution contribution) {
        // For single contribution lookup, do individual query
        boolean hasPendingClaim = false;
        Long payableId = null;
        if (contribution.getPaid() == null || !contribution.getPaid()) {
            var payableOpt = payableRepository.findByPayableTypeAndPayableRefId(PayableType.WELFARE_DUE, contribution.getId());
            if (payableOpt.isPresent()) {
                var payable = payableOpt.get();
                hasPendingClaim = payable.getStatus() == PayableStatus.AWAITING_CONFIRMATION;
                if (hasPendingClaim) {
                    payableId = payable.getId();
                }
            }
        }
        return toContributionDto(contribution, hasPendingClaim, payableId);
    }
    
    private WelfareContributionDto toContributionDto(WelfareContribution contribution, boolean hasPendingClaim) {
        return toContributionDto(contribution, hasPendingClaim, null);
    }
    
    private WelfareContributionDto toContributionDto(WelfareContribution contribution, boolean hasPendingClaim, Long payableId) {
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
                .amountFromFund(contribution.getAmountFromFund())
                .amountFromCash(contribution.getAmountFromCash())
                .paymentMethod(contribution.getPaymentMethod() != null ? contribution.getPaymentMethod().name() : null)
                .hasPendingClaim(hasPendingClaim)
                .payableId(payableId);

        if (contribution.getSourceContributionType() != null) {
            builder.sourceContributionTypeId(contribution.getSourceContributionType().getId())
                   .sourceContributionTypeName(contribution.getSourceContributionType().getName());
        }
        if (contribution.getSourceContribution() != null) {
            builder.sourceContributionId(contribution.getSourceContribution().getId());
        }

        return builder.build();
    }
    
    /**
     * Convert list of welfare contributions to DTOs with batch pending claim lookup.
     */
    private List<WelfareContributionDto> toContributionDtoListWithPendingClaims(List<WelfareContribution> contributions, Long tenantId) {
        if (contributions.isEmpty()) return List.of();
        
        // Get IDs of unpaid contributions
        List<Long> unpaidIds = contributions.stream()
                .filter(c -> c.getPaid() == null || !c.getPaid())
                .map(WelfareContribution::getId)
                .collect(Collectors.toList());
        
        // Batch fetch payable IDs with pending claims
        Map<Long, Long> pendingClaimPayableIds = new java.util.HashMap<>();
        if (!unpaidIds.isEmpty()) {
            List<Payable> pendingPayables = payableRepository.findByTenant_IdAndPayableTypeAndPayableRefIdInAndStatus(
                    tenantId, PayableType.WELFARE_DUE, unpaidIds, PayableStatus.AWAITING_CONFIRMATION);
            for (Payable p : pendingPayables) {
                pendingClaimPayableIds.put(p.getPayableRefId(), p.getId());
            }
        }
        
        return contributions.stream()
                .map(c -> {
                    boolean hasPendingClaim = pendingClaimPayableIds.containsKey(c.getId());
                    Long payableId = pendingClaimPayableIds.get(c.getId());
                    return toContributionDto(c, hasPendingClaim, payableId);
                })
                .collect(Collectors.toList());
    }

    // Inner DTO for contribution balances
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class ContributionBalanceDto {
        private Long contributionTypeId;
        private String contributionTypeName;
        private BigDecimal balance;
    }
    
    /**
     * Debug method to check welfare contributions for a member
     */
    public List<Map<String, Object>> debugGetMemberWelfareContributions(Long memberId, Long tenantId) {
        List<WelfareContribution> contributions = welfareContributionRepository.findByMemberId(memberId);
        
        return contributions.stream()
                .map(wc -> {
                    Map<String, Object> data = new java.util.HashMap<>();
                    data.put("id", wc.getId());
                    data.put("welfareEventId", wc.getWelfareEvent().getId());
                    data.put("memberId", wc.getMember().getId());
                    data.put("memberName", wc.getMember().getName());
                    data.put("amountExpected", wc.getAmountExpected());
                    data.put("amountPaid", wc.getAmountPaid());
                    data.put("amountFromFund", wc.getAmountFromFund());
                    data.put("amountFromCash", wc.getAmountFromCash());
                    data.put("paid", wc.getPaid());
                    data.put("paidFromContribution", wc.getPaidFromContribution());
                    data.put("paymentMethod", wc.getPaymentMethod() != null ? wc.getPaymentMethod().name() : null);
                    data.put("sourceContributionTypeId", wc.getSourceContributionType() != null ? wc.getSourceContributionType().getId() : null);
                    data.put("sourceContributionTypeName", wc.getSourceContributionType() != null ? wc.getSourceContributionType().getName() : null);
                    data.put("sourceContributionId", wc.getSourceContribution() != null ? wc.getSourceContribution().getId() : null);
                    data.put("tenantId", wc.getTenant().getId());
                    return data;
                })
                .collect(Collectors.toList());
    }

    // ==================== DISBURSEMENT ====================

    /**
     * Disburse welfare funds to the beneficiary.
     * Can be done partially (with whatever has been collected) or fully.
     * Creates a transaction record showing funds going out to beneficiary.
     */
    @Transactional
    public WelfareEventDto disburseWelfare(Long eventId, BigDecimal amount, Long tenantId) {
        log.info("=== DISBURSING WELFARE ===");
        log.info("eventId={}, amount={}, tenantId={}", eventId, amount, tenantId);
        
        WelfareEvent event = welfareEventRepository.findByIdAndTenantId(eventId, tenantId)
                .orElseThrow(() -> new RuntimeException("Welfare event not found"));
        
        if (event.getStatus() == WelfareEvent.WelfareStatus.CANCELLED) {
            throw new RuntimeException("Cannot disburse a cancelled welfare event");
        }
        
        if (event.getStatus() == WelfareEvent.WelfareStatus.DISBURSED) {
            throw new RuntimeException("Welfare event has already been fully disbursed");
        }
        
        // Calculate how much is available to disburse
        BigDecimal collected = event.getTotalCollected() != null ? event.getTotalCollected() : BigDecimal.ZERO;
        BigDecimal alreadyDisbursed = getAlreadyDisbursedAmount(event, tenantId);
        BigDecimal availableToDisbure = collected.subtract(alreadyDisbursed);
        
        log.info("Collected: {}, Already disbursed: {}, Available: {}", collected, alreadyDisbursed, availableToDisbure);
        
        // If no specific amount, disburse all available
        BigDecimal disburseAmount = amount != null ? amount : availableToDisbure;
        
        if (disburseAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("No funds available to disburse");
        }
        
        if (disburseAmount.compareTo(availableToDisbure) > 0) {
            throw new RuntimeException(String.format(
                    "Cannot disburse more than available. Requested: KES %,.2f, Available: KES %,.2f",
                    disburseAmount, availableToDisbure));
        }
        
        // Create disbursement transaction (OUT to beneficiary)
        Transaction disburseTx = Transaction.builder()
                .date(LocalDate.now())
                .type(Transaction.TransactionType.WELFARE)
                .description("Welfare disbursement for " + event.getEventType() + " to " + event.getMember().getName())
                .amount(disburseAmount)
                .direction(Transaction.TransactionDirection.OUT)
                .member(event.getMember())  // Beneficiary
                .referenceId(event.getId())
                .referenceType("WELFARE_DISBURSEMENT")
                .tenant(event.getTenant())
                .build();
        transactionRepository.save(disburseTx);
        
        log.info("Created disbursement transaction: amount={}, beneficiary={}", disburseAmount, event.getMember().getName());
        
        // Check status: DISBURSED only if ALL members paid AND all funds disbursed
        BigDecimal totalDisbursed = alreadyDisbursed.add(disburseAmount);
        List<WelfareContribution> contributions = welfareContributionRepository
                .findByWelfareEventIdAndTenantId(eventId, tenantId);
        boolean allPaid = contributions.stream().allMatch(c -> c.getPaid() != null && c.getPaid());
        BigDecimal totalExpected = event.getTotalExpected() != null ? event.getTotalExpected() : BigDecimal.ZERO;
        
        if (allPaid && totalDisbursed.compareTo(totalExpected) >= 0) {
            // All members paid and all funds disbursed
            event.setStatus(WelfareEvent.WelfareStatus.DISBURSED);
            log.info("All members paid and fully disbursed, status set to DISBURSED");
        } else if (totalDisbursed.compareTo(BigDecimal.ZERO) > 0) {
            // Some funds disbursed but not complete
            event.setStatus(WelfareEvent.WelfareStatus.PARTIALLY_DISBURSED);
            log.info("Partial disbursement, status set to PARTIALLY_DISBURSED");
        }
        
        welfareEventRepository.save(event);
        
        return toDto(event);
    }
    
    /**
     * Get total amount already disbursed for a welfare event
     */
    private BigDecimal getAlreadyDisbursedAmount(WelfareEvent event, Long tenantId) {
        List<Transaction> disbursements = transactionRepository.findByTenantIdAndReferenceTypeAndReferenceId(
                tenantId, "WELFARE_DISBURSEMENT", event.getId());
        
        return disbursements.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ==================== CANCELLATION ====================

    /**
     * Cancel a welfare event and reverse ONLY FUND contributions (not cash).
     * 
     * KEY LOGIC:
     * - Cash payments do NOT create finance transactions - no reversal needed
     * - Fund payments create debit transactions - these need to be credited back
     * - If already disbursed: only reverse the undisbursed FUND portion
     * - If not disbursed: reverse ALL fund portions
     * 
     * Example: Member paid KES 1000 (KES 6 from fund, KES 994 cash)
     * - If NOT disbursed yet: reverse KES 6 (credit back to fund), reset contribution
     * - If ALREADY disbursed: nothing to reverse (money went to beneficiary)
     */
    @Transactional
    public WelfareEventDto cancelWelfare(Long eventId, String reason, Long tenantId) {
        log.info("=== CANCELLING WELFARE EVENT ===");
        log.info("eventId={}, reason={}, tenantId={}", eventId, reason, tenantId);
        
        WelfareEvent event = welfareEventRepository.findByIdAndTenantId(eventId, tenantId)
                .orElseThrow(() -> new RuntimeException("Welfare event not found"));
        
        if (event.getStatus() == WelfareEvent.WelfareStatus.CANCELLED) {
            throw new RuntimeException("Welfare event is already cancelled");
        }
        
        if (event.getStatus() == WelfareEvent.WelfareStatus.DISBURSED) {
            throw new RuntimeException("Cannot cancel a fully disbursed welfare event");
        }
        
        // Calculate amounts
        BigDecimal totalCollected = event.getTotalCollected() != null ? event.getTotalCollected() : BigDecimal.ZERO;
        BigDecimal disbursedAmount = getAlreadyDisbursedAmount(event, tenantId);
        BigDecimal undisbursedAmount = totalCollected.subtract(disbursedAmount);
        
        log.info("Total collected: {}, Already disbursed: {}, Undisbursed: {}", 
                totalCollected, disbursedAmount, undisbursedAmount);
        
        // Get all contributions for this event
        List<WelfareContribution> contributions = welfareContributionRepository
                .findByWelfareEventIdAndTenantId(eventId, tenantId);
        
        // Calculate total fund portion that was collected (this is what we might need to reverse)
        BigDecimal totalFundPortionCollected = contributions.stream()
                .filter(c -> c.getAmountFromFund() != null)
                .map(WelfareContribution::getAmountFromFund)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        log.info("Total fund portion collected: {}", totalFundPortionCollected);
        
        // If already disbursed, we need to figure out how much of the FUND portion is undisbursed
        // Assumption: disbursement takes from the total collected (fund + cash mixed)
        // We reverse fund portions proportionally or up to the undisbursed amount
        BigDecimal fundAmountToReverse;
        if (disbursedAmount.compareTo(BigDecimal.ZERO) > 0) {
            // Some was disbursed. The fund portion to reverse is the MIN of:
            // - total fund collected
            // - undisbursed amount
            // Because if we collected KES 6 from fund and disbursed KES 1000, the fund portion is gone
            // But if we collected KES 100 from fund and only disbursed KES 50, we can reverse KES 50 from fund
            fundAmountToReverse = totalFundPortionCollected.min(undisbursedAmount);
        } else {
            // Nothing disbursed - reverse all fund portions
            fundAmountToReverse = totalFundPortionCollected;
        }
        
        log.info("Fund amount to reverse: {}", fundAmountToReverse);
        
        BigDecimal totalReversed = BigDecimal.ZERO;
        
        // Reverse contribution fund debit transactions (WELFARE_FROM_CONTRIBUTION)
        // Only reverse up to fundAmountToReverse
        for (WelfareContribution contribution : contributions) {
            if (fundAmountToReverse.subtract(totalReversed).compareTo(BigDecimal.ZERO) <= 0) {
                break; // We've reversed enough
            }
            
            if (contribution.getPaidFromContribution() != null && contribution.getPaidFromContribution() 
                    && contribution.getAmountFromFund() != null 
                    && contribution.getAmountFromFund().compareTo(BigDecimal.ZERO) > 0) {
                
                // Find the debit transactions for this contribution
                List<Transaction> debitTxs = transactionRepository.findByTenantIdAndReferenceTypeAndReferenceId(
                        tenantId, "WELFARE_FROM_CONTRIBUTION", contribution.getId());
                
                for (Transaction debitTx : debitTxs) {
                    BigDecimal remainingToReverse = fundAmountToReverse.subtract(totalReversed);
                    if (remainingToReverse.compareTo(BigDecimal.ZERO) <= 0) {
                        break;
                    }
                    
                    // Calculate how much to reverse from this transaction
                    BigDecimal amountToReverse = debitTx.getAmount().min(remainingToReverse);
                    
                    // Create reversal (credit) transaction
                    Transaction reversalTx = Transaction.builder()
                            .date(LocalDate.now())
                            .type(Transaction.TransactionType.WELFARE)
                            .description("REVERSAL: " + debitTx.getDescription() + " - Event cancelled" + 
                                    (reason != null ? ": " + reason : ""))
                            .amount(amountToReverse)
                            .direction(Transaction.TransactionDirection.IN)  // Credit back to fund
                            .member(debitTx.getMember())
                            .referenceId(contribution.getId())
                            .referenceType("WELFARE_CANCELLATION_REVERSAL")
                            .contributionType(debitTx.getContributionType())
                            .tenant(event.getTenant())
                            .build();
                    transactionRepository.save(reversalTx);
                    
                    totalReversed = totalReversed.add(amountToReverse);
                    
                    log.info("Reversed fund transaction for member {}: +{} to {}", 
                            debitTx.getMember().getName(), 
                            amountToReverse,
                            debitTx.getContributionType() != null ? debitTx.getContributionType().getName() : "N/A");
                }
            }
        }
        
        log.info("Total fund amount reversed: {}", totalReversed);
        
        // Reset contribution records if nothing was disbursed
        if (disbursedAmount.compareTo(BigDecimal.ZERO) == 0) {
            for (WelfareContribution contribution : contributions) {
                if (contribution.getPaid() != null && contribution.getPaid()) {
                    // Full reset - nothing was disbursed
                    contribution.setPaid(false);
                    contribution.setAmountPaid(BigDecimal.ZERO);
                    contribution.setAmountFromFund(BigDecimal.ZERO);
                    contribution.setAmountFromCash(BigDecimal.ZERO);
                    contribution.setPaidDate(null);
                    contribution.setPaidFromContribution(false);
                    contribution.setSourceContributionType(null);
                    contribution.setSourceContribution(null);
                    contribution.setPaymentMethod(null);
                    welfareContributionRepository.save(contribution);
                }
            }
        }
        
        // Update event status
        if (disbursedAmount.compareTo(BigDecimal.ZERO) > 0) {
            // Some funds were disbursed - mark as CLOSED (partial completion)
            event.setStatus(WelfareEvent.WelfareStatus.CLOSED);
            event.setTotalCollected(disbursedAmount); // Only keep what was actually disbursed
            event.setDescription((event.getDescription() != null ? event.getDescription() + "\n\n" : "") + 
                    "PARTIALLY CANCELLED on " + LocalDate.now() + ". Disbursed: KES " + disbursedAmount + 
                    (reason != null ? ". Reason: " + reason : ""));
        } else {
            // No disbursement - full cancellation
            event.setStatus(WelfareEvent.WelfareStatus.CANCELLED);
            event.setTotalCollected(BigDecimal.ZERO);
            event.setDescription((event.getDescription() != null ? event.getDescription() + "\n\n" : "") + 
                    "CANCELLED on " + LocalDate.now() + (reason != null ? ": " + reason : ""));
        }
        welfareEventRepository.save(event);
        
        log.info("=== WELFARE EVENT CANCELLED ===");
        log.info("Event {} cancelled. Disbursed preserved: {}, Fund reversed: {}", 
                eventId, disbursedAmount, totalReversed);
        
        return toDto(event);
    }
}
