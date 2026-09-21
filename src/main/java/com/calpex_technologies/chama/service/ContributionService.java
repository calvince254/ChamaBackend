package com.calpex_technologies.chama.service;

import com.calpex_technologies.chama.dto.ContributionDto;
import com.calpex_technologies.chama.dto.PageResponse;
import com.calpex_technologies.chama.entity.*;
import com.calpex_technologies.chama.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ContributionService {

    private final ContributionRepository contributionRepository;
    private final MemberRepository memberRepository;
    private final TenantRepository tenantRepository;
    private final TransactionRepository transactionRepository;
    private final ContributionTypeRepository contributionTypeRepository;
    private final PayableRepository payableRepository;
    private final PaymentClaimRepository paymentClaimRepository;
    
    private static final int DEFAULT_PAGE_SIZE = 10;

    public List<ContributionDto> getAllContributions(Long tenantId) {
        List<Contribution> contributions = contributionRepository.findByTenantIdOrderByMonthDesc(tenantId);
        return toDtoListWithPendingClaims(contributions, tenantId);
    }
    
    /**
     * Get contributions with pagination by type and month.
     */
    public PageResponse<ContributionDto> getContributionsPaginated(Long tenantId, Long contributionTypeId, 
                                                                    YearMonth month, String status,
                                                                    int page, int size) {
        if (size <= 0) size = DEFAULT_PAGE_SIZE;
        Pageable pageable = PageRequest.of(page, size, Sort.by("member.name").ascending());
        
        Page<Contribution> contributionPage;
        
        if (status != null && !status.equalsIgnoreCase("ALL") && !status.trim().isEmpty()) {
            Contribution.ContributionStatus contribStatus = Contribution.ContributionStatus.valueOf(status.toUpperCase());
            contributionPage = contributionRepository.findByTenantIdAndContributionTypeIdAndMonthAndStatus(
                    tenantId, contributionTypeId, month, contribStatus, pageable);
        } else {
            contributionPage = contributionRepository.findByTenantIdAndContributionTypeIdAndMonth(
                    tenantId, contributionTypeId, month, pageable);
        }
        
        // Batch fetch pending payables for this page (need full Payable to get payableId)
        List<Contribution> contributions = contributionPage.getContent();
        List<Long> unpaidIds = contributions.stream()
                .filter(c -> c.getStatus() != Contribution.ContributionStatus.PAID)
                .map(Contribution::getId)
                .collect(Collectors.toList());
        
        // Map contributionId -> payableId for awaiting confirmation payables
        Map<Long, Long> pendingClaimPayableIds = new java.util.HashMap<>();
        if (!unpaidIds.isEmpty()) {
            List<Payable> pendingPayables = payableRepository.findByTenant_IdAndPayableTypeAndPayableRefIdInAndStatus(
                    tenantId, PayableType.CONTRIBUTION, unpaidIds, PayableStatus.AWAITING_CONFIRMATION);
            for (Payable p : pendingPayables) {
                pendingClaimPayableIds.put(p.getPayableRefId(), p.getId());
            }
        }
        
        Page<ContributionDto> dtoPage = contributionPage.map(c -> {
            boolean hasPending = pendingClaimPayableIds.containsKey(c.getId());
            Long payableId = hasPending ? pendingClaimPayableIds.get(c.getId()) : null;
            return toDto(c, hasPending, payableId);
        });
        return PageResponse.from(dtoPage);
    }

    public List<ContributionDto> getContributionsByMonth(Long tenantId, YearMonth month) {
        List<Contribution> contributions = contributionRepository.findByTenantIdOrderByMonthDesc(tenantId).stream()
                .filter(c -> c.getMonth().equals(month))
                .collect(Collectors.toList());
        return toDtoListWithPendingClaims(contributions, tenantId);
    }

    public List<ContributionDto> getContributionsByTypeAndMonth(Long tenantId, Long contributionTypeId, YearMonth month) {
        ContributionType contributionType = contributionTypeRepository.findByIdAndTenantId(contributionTypeId, tenantId)
                .orElseThrow(() -> new RuntimeException("Contribution type not found"));

        // Get all active members
        List<Member> activeMembers = memberRepository.findByTenantIdAndStatus(tenantId, Member.MemberStatus.ACTIVE);
        
        // Get existing contributions for this type and month
        List<Contribution> existingContributions = contributionRepository
                .findByTenantIdAndContributionTypeIdAndMonth(tenantId, contributionTypeId, month);
        
        // Batch fetch pending claims for existing unpaid contributions
        List<Long> unpaidContributionIds = existingContributions.stream()
                .filter(c -> c.getStatus() != Contribution.ContributionStatus.PAID)
                .map(Contribution::getId)
                .collect(Collectors.toList());
        
        Map<Long, Long> pendingClaimPayableIds = new java.util.HashMap<>();
        if (!unpaidContributionIds.isEmpty()) {
            List<Payable> pendingPayables = payableRepository.findByTenant_IdAndPayableTypeAndPayableRefIdInAndStatus(
                    tenantId, PayableType.CONTRIBUTION, unpaidContributionIds, PayableStatus.AWAITING_CONFIRMATION);
            for (Payable p : pendingPayables) {
                pendingClaimPayableIds.put(p.getPayableRefId(), p.getId());
            }
        }
        
        // Create a map of memberId -> contribution for quick lookup
        java.util.Map<Long, Contribution> contributionMap = existingContributions.stream()
                .collect(java.util.stream.Collectors.toMap(c -> c.getMember().getId(), c -> c));
        
        // Build result: show all active members with their contribution status
        List<ContributionDto> result = new ArrayList<>();
        
        int dueDay = contributionType.getDueDay() != null ? contributionType.getDueDay() : 1;
        LocalDate dueDate = month.atDay(Math.min(dueDay, month.lengthOfMonth()));
        
        for (Member member : activeMembers) {
            Contribution existing = contributionMap.get(member.getId());
            
            if (existing != null) {
                // Member has a contribution record - check for pending claim
                boolean hasPending = pendingClaimPayableIds.containsKey(existing.getId());
                Long payableId = hasPending ? pendingClaimPayableIds.get(existing.getId()) : null;
                result.add(toDto(existing, hasPending, payableId));
            } else {
                // Member doesn't have a record yet - show as pending
                ContributionDto dto = ContributionDto.builder()
                        .id(null) // No ID yet - not saved
                        .memberId(member.getId())
                        .memberName(member.getName())
                        .memberNo(member.getMemberNo())
                        .month(month.toString())
                        .amountDue(contributionType.getAmount())
                        .amountPaid(BigDecimal.ZERO)
                        .status("PENDING")
                        .penalty(BigDecimal.ZERO)
                        .penaltyApplied(false)
                        .dueDate(dueDate.toString())
                        .contributionTypeId(contributionType.getId())
                        .contributionTypeName(contributionType.getName())
                        .hasPendingClaim(false)
                        .payableId(null)
                        .build();
                result.add(dto);
            }
        }
        
        return result;
    }

    /**
     * Get only saved contributions (no virtual pending records)
     */
    public List<ContributionDto> getSavedContributionsByTypeAndMonth(Long tenantId, Long contributionTypeId, YearMonth month) {
        List<Contribution> contributions = contributionRepository
                .findByTenantIdAndContributionTypeIdAndMonth(tenantId, contributionTypeId, month);
        return toDtoListWithPendingClaims(contributions, tenantId);
    }

    /**
     * Get active members who don't have a contribution record for the given type and month
     */
    public List<java.util.Map<String, Object>> getMembersWithoutContribution(Long tenantId, Long contributionTypeId, YearMonth month) {
        // Get all active members
        List<Member> activeMembers = memberRepository.findByTenantIdAndStatus(tenantId, Member.MemberStatus.ACTIVE);
        
        // Get existing contributions for this type and month
        List<Contribution> existingContributions = contributionRepository
                .findByTenantIdAndContributionTypeIdAndMonth(tenantId, contributionTypeId, month);
        
        // Get member IDs that already have contributions
        java.util.Set<Long> membersWithContributions = existingContributions.stream()
                .map(c -> c.getMember().getId())
                .collect(java.util.stream.Collectors.toSet());
        
        // Return members without contributions
        return activeMembers.stream()
                .filter(m -> !membersWithContributions.contains(m.getId()))
                .map(m -> {
                    java.util.Map<String, Object> result = new java.util.HashMap<>();
                    result.put("id", m.getId());
                    result.put("name", m.getName());
                    result.put("memberNo", m.getMemberNo());
                    return result;
                })
                .collect(Collectors.toList());
    }

    public ContributionDto getContribution(Long id, Long tenantId) {
        Contribution contribution = contributionRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Contribution not found"));
        return toDto(contribution);
    }

    @Transactional
    public ContributionDto createContribution(ContributionDto dto, Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        Member member = memberRepository.findByIdAndTenantId(dto.getMemberId(), tenantId)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        YearMonth month = YearMonth.parse(dto.getMonth());

        Contribution contribution = Contribution.builder()
                .member(member)
                .month(month)
                .amountDue(dto.getAmountDue())
                .amountPaid(dto.getAmountPaid() != null ? dto.getAmountPaid() : BigDecimal.ZERO)
                .penalty(dto.getPenalty() != null ? dto.getPenalty() : BigDecimal.ZERO)
                .tenant(tenant)
                .build();

        // Link to contribution type if provided
        if (dto.getContributionTypeId() != null) {
            ContributionType ct = contributionTypeRepository.findByIdAndTenantId(dto.getContributionTypeId(), tenantId)
                    .orElse(null);
            contribution.setContributionType(ct);
        }

        updateContributionStatus(contribution);
        
        if (contribution.getAmountPaid().compareTo(BigDecimal.ZERO) > 0) {
            contribution.setPaidOn(LocalDate.now());
        }

        contribution = contributionRepository.save(contribution);

        // Create transaction record
        if (contribution.getAmountPaid().compareTo(BigDecimal.ZERO) > 0) {
            createTransaction(contribution, tenant);
        }

        // Update member savings
        updateMemberSavings(member);

        return toDto(contribution);
    }

    /**
     * Generate contribution records for selected members (or all if memberIds is empty/null)
     */
    @Transactional
    public List<ContributionDto> generateContributionsForMembers(Long tenantId, Long contributionTypeId, 
                                                                   YearMonth month, List<Long> memberIds) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        ContributionType contributionType = contributionTypeRepository.findByIdAndTenantId(contributionTypeId, tenantId)
                .orElseThrow(() -> new RuntimeException("Contribution type not found"));

        if (!contributionType.isActive()) {
            throw new RuntimeException("Contribution type is not active");
        }

        // Get members to generate for
        List<Member> targetMembers;
        if (memberIds == null || memberIds.isEmpty()) {
            // All active members
            targetMembers = memberRepository.findByTenantIdAndStatus(tenantId, Member.MemberStatus.ACTIVE);
        } else {
            // Specific members
            targetMembers = memberRepository.findAllById(memberIds).stream()
                    .filter(m -> m.getTenant().getId().equals(tenantId) && m.getStatus() == Member.MemberStatus.ACTIVE)
                    .collect(Collectors.toList());
        }
        
        if (targetMembers.isEmpty()) {
            throw new RuntimeException("No valid members found");
        }

        List<ContributionDto> created = new ArrayList<>();
        int dueDay = contributionType.getDueDay() != null ? contributionType.getDueDay() : 1;
        LocalDate dueDate = month.atDay(Math.min(dueDay, month.lengthOfMonth()));

        for (Member member : targetMembers) {
            // Check if contribution already exists for this member/type/month
            boolean exists = contributionRepository.existsByContributionTypeIdAndMemberIdAndMonth(
                    contributionTypeId, member.getId(), month);

            if (!exists) {
                Contribution contribution = Contribution.builder()
                        .tenant(tenant)
                        .member(member)
                        .contributionType(contributionType)
                        .month(month)
                        .amountDue(contributionType.getAmount())
                        .amountPaid(BigDecimal.ZERO)
                        .penalty(BigDecimal.ZERO)
                        .status(Contribution.ContributionStatus.PENDING)
                        .dueDate(dueDate)
                        .penaltyApplied(false)
                        .build();

                contribution = contributionRepository.save(contribution);
                created.add(toDto(contribution));
                log.info("Created contribution for member {} type {} month {}", 
                        member.getName(), contributionType.getName(), month);
            }
        }

        return created;
    }

    /**
     * Generate contribution records for all active members for a specific contribution type and month
     */
    @Transactional
    public List<ContributionDto> generateMonthlyContributions(Long tenantId, Long contributionTypeId, YearMonth month) {
        return generateContributionsForMembers(tenantId, contributionTypeId, month, null);
    }

    /**
     * Generate contributions for all active contribution types for a given month
     */
    @Transactional
    public int generateAllMonthlyContributions(Long tenantId, YearMonth month) {
        List<ContributionType> activeTypes = contributionTypeRepository.findByTenantIdAndActiveTrue(tenantId);
        int totalCreated = 0;

        for (ContributionType ct : activeTypes) {
            try {
                List<ContributionDto> created = generateMonthlyContributions(tenantId, ct.getId(), month);
                totalCreated += created.size();
            } catch (Exception e) {
                log.error("Error generating contributions for type {}: {}", ct.getName(), e.getMessage());
            }
        }

        return totalCreated;
    }

    @Transactional
    public ContributionDto updateContribution(Long id, ContributionDto dto, Long tenantId) {
        Contribution contribution = contributionRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Contribution not found"));

        BigDecimal previousAmount = contribution.getAmountPaid();
        
        contribution.setAmountDue(dto.getAmountDue());
        contribution.setAmountPaid(dto.getAmountPaid());
        contribution.setPenalty(dto.getPenalty());
        
        if (dto.getPaidOn() != null) {
            contribution.setPaidOn(LocalDate.parse(dto.getPaidOn()));
        }
        
        updateContributionStatus(contribution);
        contribution = contributionRepository.save(contribution);

        // Update member savings
        updateMemberSavings(contribution.getMember());

        return toDto(contribution);
    }

    /**
     * Record a payment for a contribution (supports partial payments)
     * If contribution record doesn't exist, creates it first
     */
    @Transactional
    public ContributionDto recordPayment(Long id, BigDecimal amount, Long tenantId) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Payment amount must be greater than 0");
        }

        Contribution contribution = contributionRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Contribution not found"));

        BigDecimal currentPaid = contribution.getAmountPaid() != null ? contribution.getAmountPaid() : BigDecimal.ZERO;
        BigDecimal newPaid = currentPaid.add(amount);
        
        contribution.setAmountPaid(newPaid);
        contribution.setPaidOn(LocalDate.now());
        
        updateContributionStatus(contribution);
        contribution = contributionRepository.save(contribution);

        // Create transaction record
        createTransaction(contribution, contribution.getTenant(), amount);

        // Update member savings
        updateMemberSavings(contribution.getMember());

        return toDto(contribution);
    }

    /**
     * Record a payment for a member who may not have a contribution record yet
     */
    @Transactional
    public ContributionDto recordPaymentForMember(Long tenantId, Long memberId, Long contributionTypeId, 
                                                   YearMonth month, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Payment amount must be greater than 0");
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));
        Member member = memberRepository.findByIdAndTenantId(memberId, tenantId)
                .orElseThrow(() -> new RuntimeException("Member not found"));
        ContributionType contributionType = contributionTypeRepository.findByIdAndTenantId(contributionTypeId, tenantId)
                .orElseThrow(() -> new RuntimeException("Contribution type not found"));

        // Check if contribution exists
        Contribution contribution = contributionRepository
                .findByContributionTypeIdAndMemberIdAndMonth(contributionTypeId, memberId, month)
                .orElse(null);

        if (contribution == null) {
            // Create new contribution record
            int dueDay = contributionType.getDueDay() != null ? contributionType.getDueDay() : 1;
            LocalDate dueDate = month.atDay(Math.min(dueDay, month.lengthOfMonth()));
            
            contribution = Contribution.builder()
                    .tenant(tenant)
                    .member(member)
                    .contributionType(contributionType)
                    .month(month)
                    .amountDue(contributionType.getAmount())
                    .amountPaid(amount)
                    .penalty(BigDecimal.ZERO)
                    .dueDate(dueDate)
                    .paidOn(LocalDate.now())
                    .penaltyApplied(false)
                    .build();
        } else {
            // Update existing
            BigDecimal currentPaid = contribution.getAmountPaid() != null ? contribution.getAmountPaid() : BigDecimal.ZERO;
            contribution.setAmountPaid(currentPaid.add(amount));
            contribution.setPaidOn(LocalDate.now());
        }

        updateContributionStatus(contribution);
        contribution = contributionRepository.save(contribution);

        // Create transaction record
        createTransaction(contribution, tenant, amount);

        // Update member savings
        updateMemberSavings(member);

        return toDto(contribution);
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

    private void createTransaction(Contribution contribution, Tenant tenant) {
        createTransaction(contribution, tenant, contribution.getAmountPaid());
    }

    private void createTransaction(Contribution contribution, Tenant tenant, BigDecimal amount) {
        String typeName = contribution.getContributionType() != null 
                ? contribution.getContributionType().getName() 
                : "Monthly contribution";
        
        Transaction transaction = Transaction.builder()
                .date(LocalDate.now())
                .type(Transaction.TransactionType.CONTRIBUTION)
                .description(typeName + " from " + contribution.getMember().getName() + " for " + contribution.getMonth())
                .amount(amount)
                .direction(Transaction.TransactionDirection.IN)
                .member(contribution.getMember())
                .referenceId(contribution.getId())
                .referenceType("CONTRIBUTION")
                .tenant(tenant)
                .build();
        transactionRepository.save(transaction);
    }

    private void updateMemberSavings(Member member) {
        BigDecimal total = contributionRepository.sumAmountPaidByTenantId(member.getTenant().getId());
        member.setTotalSavings(total != null ? total : BigDecimal.ZERO);
        memberRepository.save(member);
    }

    private ContributionDto toDto(Contribution contribution) {
        // For single contribution lookup (used by record payment), do individual query
        boolean hasPendingClaim = false;
        Long payableId = null;
        if (contribution.getStatus() != Contribution.ContributionStatus.PAID) {
            var payableOpt = payableRepository.findByPayableTypeAndPayableRefIdAndTenant_Id(
                    PayableType.CONTRIBUTION, contribution.getId(), contribution.getTenant().getId());
            if (payableOpt.isPresent()) {
                Payable payable = payableOpt.get();
                hasPendingClaim = payable.getStatus() == PayableStatus.AWAITING_CONFIRMATION;
                if (hasPendingClaim) {
                    payableId = payable.getId();
                }
            }
        }
        return toDto(contribution, hasPendingClaim, payableId);
    }
    
    private ContributionDto toDto(Contribution contribution, boolean hasPendingClaim) {
        return toDto(contribution, hasPendingClaim, null);
    }
    
    private ContributionDto toDto(Contribution contribution, boolean hasPendingClaim, Long payableId) {
        ContributionDto.ContributionDtoBuilder builder = ContributionDto.builder()
                .id(contribution.getId())
                .memberId(contribution.getMember().getId())
                .memberName(contribution.getMember().getName())
                .memberNo(contribution.getMember().getMemberNo())
                .month(contribution.getMonth().toString())
                .amountDue(contribution.getAmountDue())
                .amountPaid(contribution.getAmountPaid())
                .status(contribution.getStatus().name())
                .penalty(contribution.getPenalty())
                .penaltyApplied(contribution.isPenaltyApplied())
                .paidOn(contribution.getPaidOn() != null ? contribution.getPaidOn().toString() : null)
                .dueDate(contribution.getDueDate() != null ? contribution.getDueDate().toString() : null)
                .hasPendingClaim(hasPendingClaim)
                .payableId(payableId);

        if (contribution.getContributionType() != null) {
            builder.contributionTypeId(contribution.getContributionType().getId())
                   .contributionTypeName(contribution.getContributionType().getName());
        }

        return builder.build();
    }
    
    /**
     * Convert list of contributions to DTOs with batch pending claim lookup.
     */
    private List<ContributionDto> toDtoListWithPendingClaims(List<Contribution> contributions, Long tenantId) {
        if (contributions.isEmpty()) return List.of();
        
        // Get IDs of unpaid contributions
        List<Long> unpaidIds = contributions.stream()
                .filter(c -> c.getStatus() != Contribution.ContributionStatus.PAID)
                .map(Contribution::getId)
                .collect(Collectors.toList());
        
        // Batch fetch payables with pending claims (maps refId -> payableId)
        Map<Long, Long> pendingClaimPayableIds = new java.util.HashMap<>();
        if (!unpaidIds.isEmpty()) {
            List<Payable> pendingPayables = payableRepository.findByTenant_IdAndPayableTypeAndPayableRefIdInAndStatus(
                    tenantId, PayableType.CONTRIBUTION, unpaidIds, PayableStatus.AWAITING_CONFIRMATION);
            for (Payable p : pendingPayables) {
                pendingClaimPayableIds.put(p.getPayableRefId(), p.getId());
            }
        }
        
        return contributions.stream()
                .map(c -> {
                    boolean hasPending = pendingClaimPayableIds.containsKey(c.getId());
                    Long pId = hasPending ? pendingClaimPayableIds.get(c.getId()) : null;
                    return toDto(c, hasPending, pId);
                })
                .collect(Collectors.toList());
    }
}
