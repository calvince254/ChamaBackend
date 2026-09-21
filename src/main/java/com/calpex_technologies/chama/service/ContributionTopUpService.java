package com.calpex_technologies.chama.service;

import com.calpex_technologies.chama.dto.ContributionTopUpDto;
import com.calpex_technologies.chama.dto.PageResponse;
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
public class ContributionTopUpService {

    private final ContributionTopUpRepository topUpRepository;
    private final ContributionRepository contributionRepository;
    private final ContributionTypeRepository contributionTypeRepository;
    private final MemberRepository memberRepository;
    private final TenantRepository tenantRepository;
    private final TransactionRepository transactionRepository;

    /**
     * Get all top-up requests with pagination and filters
     */
    public PageResponse<ContributionTopUpDto> getTopUps(Long tenantId, String status, Long memberId, 
                                                         Long contributionTypeId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        
        ContributionTopUp.TopUpStatus topUpStatus = null;
        if (status != null && !status.isEmpty() && !status.equalsIgnoreCase("ALL")) {
            topUpStatus = ContributionTopUp.TopUpStatus.valueOf(status.toUpperCase());
        }
        
        Page<ContributionTopUp> topUpPage = topUpRepository.findWithFilters(
                tenantId, topUpStatus, memberId, contributionTypeId, pageable);
        
        return PageResponse.from(topUpPage.map(this::toDto));
    }

    /**
     * Get top-ups for a specific member
     */
    public PageResponse<ContributionTopUpDto> getMyTopUps(Long tenantId, Long memberId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ContributionTopUp> topUpPage = topUpRepository.findByTenantIdAndMemberId(tenantId, memberId, pageable);
        return PageResponse.from(topUpPage.map(this::toDto));
    }

    /**
     * Get a single top-up request
     */
    public ContributionTopUpDto getTopUp(Long id, Long tenantId) {
        ContributionTopUp topUp = topUpRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Top-up request not found"));
        return toDto(topUp);
    }

    /**
     * Create a new top-up request (member initiates)
     */
    @Transactional
    public ContributionTopUpDto createTopUp(ContributionTopUpDto dto, Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        Member member = memberRepository.findByIdAndTenantId(dto.getMemberId(), tenantId)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        ContributionType contributionType = contributionTypeRepository.findByIdAndTenantId(dto.getContributionTypeId(), tenantId)
                .orElseThrow(() -> new RuntimeException("Contribution type not found"));

        if (dto.getAmount() == null || dto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Amount must be greater than 0");
        }

        ContributionTopUp topUp = ContributionTopUp.builder()
                .tenant(tenant)
                .member(member)
                .contributionType(contributionType)
                .amount(dto.getAmount())
                .status(ContributionTopUp.TopUpStatus.PENDING)
                .requestDate(LocalDateTime.now())
                .paymentReference(dto.getPaymentReference())
                .notes(dto.getNotes())
                .targetMonth(dto.getTargetMonth() != null ? YearMonth.parse(dto.getTargetMonth()) : null)
                .description(dto.getDescription())
                .build();

        topUp = topUpRepository.save(topUp);
        log.info("Created top-up request {} for member {} amount {}", topUp.getId(), member.getName(), dto.getAmount());
        
        return toDto(topUp);
    }

    /**
     * Approve a top-up request (admin action)
     * This will:
     * 1. Mark the request as approved
     * 2. Find or create a contribution record for current month
     * 3. Add the amount to the contribution
     * 4. Create a transaction record
     */
    @Transactional
    public ContributionTopUpDto approveTopUp(Long id, Long approverMemberId, Long tenantId) {
        ContributionTopUp topUp = topUpRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Top-up request not found"));

        if (topUp.getStatus() != ContributionTopUp.TopUpStatus.PENDING) {
            throw new RuntimeException("Top-up request is not pending");
        }

        Member approver = memberRepository.findByIdAndTenantId(approverMemberId, tenantId)
                .orElseThrow(() -> new RuntimeException("Approver not found"));

        // Mark as approved
        topUp.setStatus(ContributionTopUp.TopUpStatus.APPROVED);
        topUp.setApprovalDate(LocalDateTime.now());
        topUp.setApprovedBy(approver);
        topUp = topUpRepository.save(topUp);

        // Find or create contribution for target month (or current month if not specified)
        YearMonth targetMonth = topUp.getTargetMonth() != null ? topUp.getTargetMonth() : YearMonth.now();
        Contribution contribution = contributionRepository
                .findByContributionTypeIdAndMemberIdAndMonth(
                        topUp.getContributionType().getId(), 
                        topUp.getMember().getId(), 
                        targetMonth)
                .orElse(null);

        if (contribution == null) {
            // Create new contribution record
            int dueDay = topUp.getContributionType().getDueDay() != null 
                    ? topUp.getContributionType().getDueDay() : 1;
            LocalDate dueDate = targetMonth.atDay(Math.min(dueDay, targetMonth.lengthOfMonth()));
            
            contribution = Contribution.builder()
                    .tenant(topUp.getTenant())
                    .member(topUp.getMember())
                    .contributionType(topUp.getContributionType())
                    .month(targetMonth)
                    .amountDue(topUp.getContributionType().getAmount())
                    .amountPaid(topUp.getAmount())
                    .dueDate(dueDate)
                    .paidOn(LocalDate.now())
                    .penalty(BigDecimal.ZERO)
                    .penaltyApplied(false)
                    .build();
        } else {
            // Add to existing contribution
            BigDecimal currentPaid = contribution.getAmountPaid() != null 
                    ? contribution.getAmountPaid() : BigDecimal.ZERO;
            contribution.setAmountPaid(currentPaid.add(topUp.getAmount()));
            contribution.setPaidOn(LocalDate.now());
        }

        // Update status
        updateContributionStatus(contribution);
        contribution = contributionRepository.save(contribution);

        // Create transaction
        createTransaction(topUp, contribution);

        log.info("Approved top-up {} - added {} to contribution {} for member {}", 
                topUp.getId(), topUp.getAmount(), contribution.getId(), topUp.getMember().getName());

        return toDto(topUp);
    }

    /**
     * Reject a top-up request (admin action)
     */
    @Transactional
    public ContributionTopUpDto rejectTopUp(Long id, String reason, Long approverMemberId, Long tenantId) {
        ContributionTopUp topUp = topUpRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Top-up request not found"));

        if (topUp.getStatus() != ContributionTopUp.TopUpStatus.PENDING) {
            throw new RuntimeException("Top-up request is not pending");
        }

        Member approver = memberRepository.findByIdAndTenantId(approverMemberId, tenantId)
                .orElseThrow(() -> new RuntimeException("Approver not found"));

        topUp.setStatus(ContributionTopUp.TopUpStatus.REJECTED);
        topUp.setApprovalDate(LocalDateTime.now());
        topUp.setApprovedBy(approver);
        topUp.setRejectionReason(reason);
        topUp = topUpRepository.save(topUp);

        log.info("Rejected top-up {} for member {} - reason: {}", 
                topUp.getId(), topUp.getMember().getName(), reason);

        return toDto(topUp);
    }

    /**
     * Get count of pending top-ups
     */
    public long getPendingCount(Long tenantId) {
        return topUpRepository.countByTenantIdAndStatus(tenantId, ContributionTopUp.TopUpStatus.PENDING);
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

    private void createTransaction(ContributionTopUp topUp, Contribution contribution) {
        String typeName = topUp.getContributionType().getName();
        String description = topUp.getDescription() != null && !topUp.getDescription().isEmpty()
                ? topUp.getDescription()
                : "Manual top-up: " + typeName + " from " + topUp.getMember().getName();
        
        // Include month info if specified
        if (topUp.getTargetMonth() != null) {
            description += " for " + topUp.getTargetMonth().toString();
        }
        
        Transaction transaction = Transaction.builder()
                .date(LocalDate.now())
                .type(Transaction.TransactionType.CONTRIBUTION)
                .description(description)
                .amount(topUp.getAmount())
                .direction(Transaction.TransactionDirection.IN)
                .member(topUp.getMember())
                .referenceId(contribution.getId())
                .referenceType("CONTRIBUTION_TOPUP")
                .tenant(topUp.getTenant())
                .build();
        transactionRepository.save(transaction);
    }

    private ContributionTopUpDto toDto(ContributionTopUp topUp) {
        ContributionTopUpDto.ContributionTopUpDtoBuilder builder = ContributionTopUpDto.builder()
                .id(topUp.getId())
                .memberId(topUp.getMember().getId())
                .memberName(topUp.getMember().getName())
                .memberNo(topUp.getMember().getMemberNo())
                .contributionTypeId(topUp.getContributionType().getId())
                .contributionTypeName(topUp.getContributionType().getName())
                .amount(topUp.getAmount())
                .status(topUp.getStatus().name())
                .requestDate(topUp.getRequestDate() != null ? topUp.getRequestDate().toString() : null)
                .approvalDate(topUp.getApprovalDate() != null ? topUp.getApprovalDate().toString() : null)
                .rejectionReason(topUp.getRejectionReason())
                .paymentReference(topUp.getPaymentReference())
                .notes(topUp.getNotes())
                .targetMonth(topUp.getTargetMonth() != null ? topUp.getTargetMonth().toString() : null)
                .description(topUp.getDescription());

        if (topUp.getApprovedBy() != null) {
            builder.approvedById(topUp.getApprovedBy().getId())
                   .approvedByName(topUp.getApprovedBy().getName());
        }

        return builder.build();
    }
}
