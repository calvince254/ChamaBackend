package com.calpex_technologies.chama.service;

import com.calpex_technologies.chama.dto.*;
import com.calpex_technologies.chama.entity.*;
import com.calpex_technologies.chama.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvestmentService {

    private final InvestmentRepository investmentRepository;
    private final InvestmentValueHistoryRepository valueHistoryRepository;
    private final InvestmentApprovalRepository approvalRepository;
    private final InvestmentTopupRepository investmentTopupRepository;
    private final MemberRepository memberRepository;
    private final TenantRepository tenantRepository;
    private final TransactionRepository transactionRepository;

    public List<InvestmentDto> getAllInvestments(Long tenantId) {
        return investmentRepository.findByTenantIdOrderByDateInvestedDesc(tenantId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public Page<InvestmentDto> getInvestmentsPaginated(Long tenantId, int page, int size, String status, String approvalStatus) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Investment> investmentPage;
        
        boolean hasStatus = status != null && !status.isEmpty();
        boolean hasApprovalStatus = approvalStatus != null && !approvalStatus.isEmpty();
        
        if (hasStatus && hasApprovalStatus) {
            Investment.InvestmentStatus investmentStatus = Investment.InvestmentStatus.valueOf(status);
            Investment.ApprovalStatus invApprovalStatus = Investment.ApprovalStatus.valueOf(approvalStatus);
            investmentPage = investmentRepository.findByTenantIdAndStatusAndApprovalStatus(tenantId, investmentStatus, invApprovalStatus, pageable);
        } else if (hasStatus) {
            Investment.InvestmentStatus investmentStatus = Investment.InvestmentStatus.valueOf(status);
            investmentPage = investmentRepository.findByTenantIdAndStatus(tenantId, investmentStatus, pageable);
        } else if (hasApprovalStatus) {
            Investment.ApprovalStatus invApprovalStatus = Investment.ApprovalStatus.valueOf(approvalStatus);
            investmentPage = investmentRepository.findByTenantIdAndApprovalStatus(tenantId, invApprovalStatus, pageable);
        } else {
            investmentPage = investmentRepository.findByTenantIdOrderByDateInvestedDesc(tenantId, pageable);
        }
        
        return investmentPage.map(this::toDto);
    }

    public InvestmentDto getInvestment(Long id, Long tenantId) {
        Investment investment = investmentRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Investment not found"));
        return toDto(investment);
    }

    @Transactional
    public InvestmentDto createInvestment(InvestmentDto dto, Long tenantId, Long creatorMemberId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        Member creatorMember = null;
        if (creatorMemberId != null) {
            creatorMember = memberRepository.findById(creatorMemberId).orElse(null);
        }

        // Determine if approval is required
        boolean requiresApproval = dto.getRequiresApproval() != null ? dto.getRequiresApproval() : true;
        List<Long> approverIds = dto.getApproverIds();
        
        // If no approvers selected or requiresApproval is false, auto-approve
        if (!requiresApproval || approverIds == null || approverIds.isEmpty()) {
            requiresApproval = false;
        }

        Investment investment = Investment.builder()
                .name(dto.getName())
                .category(Investment.InvestmentCategory.valueOf(dto.getCategory()))
                .amountInvested(dto.getAmountInvested())
                .currentValue(dto.getAmountInvested())
                .approvalStatus(requiresApproval ? Investment.ApprovalStatus.PENDING : Investment.ApprovalStatus.APPROVED)
                .status(Investment.InvestmentStatus.ACTIVE)
                .description(dto.getDescription())
                .requiresApproval(requiresApproval)
                .requiredApprovals(approverIds != null ? approverIds.size() : 0)
                .createdByMember(creatorMember)
                .tenant(tenant)
                .build();

        // If auto-approved, set invested date now
        if (!requiresApproval) {
            investment.setDateInvested(LocalDate.now());
            investment.setApprovedBy("Auto-approved (no approvers required)");
            investment.setApprovedAt(LocalDateTime.now());
        }

        investment = investmentRepository.save(investment);
        
        // Create approval records for each approver
        if (requiresApproval && approverIds != null && !approverIds.isEmpty()) {
            for (Long approverId : approverIds) {
                Member approver = memberRepository.findById(approverId)
                        .orElseThrow(() -> new RuntimeException("Approver not found: " + approverId));
                
                InvestmentApproval approval = InvestmentApproval.builder()
                        .investment(investment)
                        .approver(approver)
                        .decision(InvestmentApproval.ApprovalDecision.PENDING)
                        .build();
                approvalRepository.save(approval);
            }
        }
        
        // Create initial value history entry
        createValueHistoryEntry(investment, dto.getAmountInvested(), null, "Initial investment value");

        // If auto-approved, create transaction immediately
        if (!requiresApproval) {
            createInvestmentTransaction(investment);
        }
        
        // Re-fetch to get all relationships loaded properly
        return toDto(investmentRepository.findByIdAndTenantId(investment.getId(), tenantId).orElse(investment));
    }

    // Individual approver submits their vote
    @Transactional
    public InvestmentDto submitApproval(Long investmentId, Long tenantId, Long memberId, boolean approve, String comments) {
        Investment investment = investmentRepository.findByIdAndTenantId(investmentId, tenantId)
                .orElseThrow(() -> new RuntimeException("Investment not found"));
        
        if (investment.getApprovalStatus() != Investment.ApprovalStatus.PENDING) {
            throw new RuntimeException("Investment is not pending approval");
        }

        InvestmentApproval approval = approvalRepository.findByInvestmentIdAndApproverId(investmentId, memberId)
                .orElseThrow(() -> new RuntimeException("You are not an approver for this investment"));
        
        if (approval.getDecision() != InvestmentApproval.ApprovalDecision.PENDING) {
            throw new RuntimeException("You have already voted on this investment");
        }

        approval.setDecision(approve ? InvestmentApproval.ApprovalDecision.APPROVED : InvestmentApproval.ApprovalDecision.REJECTED);
        approval.setDecidedAt(LocalDateTime.now());
        approval.setComments(comments);
        approvalRepository.save(approval);

        // Check if we have enough approvals or any rejections
        checkAndUpdateInvestmentStatus(investment);

        return toDto(investmentRepository.findById(investmentId).get());
    }

    private void checkAndUpdateInvestmentStatus(Investment investment) {
        long approvalCount = investment.getApprovalCount();
        long rejectionCount = investment.getRejectionCount();
        int required = investment.getRequiredApprovals() != null ? investment.getRequiredApprovals() : 1;

        // If any rejection, reject the investment
        if (rejectionCount > 0) {
            investment.setApprovalStatus(Investment.ApprovalStatus.REJECTED);
            // Get rejection reason from the rejector
            investment.getApprovals().stream()
                    .filter(a -> a.getDecision() == InvestmentApproval.ApprovalDecision.REJECTED)
                    .findFirst()
                    .ifPresent(a -> investment.setRejectionReason(a.getComments()));
            investmentRepository.save(investment);
            return;
        }

        // If all required approvers have approved
        if (approvalCount >= required) {
            investment.setApprovalStatus(Investment.ApprovalStatus.APPROVED);
            investment.setDateInvested(LocalDate.now());
            investment.setApprovedAt(LocalDateTime.now());
            
            // Set approvedBy to list of approvers
            String approvers = investment.getApprovals().stream()
                    .filter(a -> a.getDecision() == InvestmentApproval.ApprovalDecision.APPROVED)
                    .map(a -> a.getApprover().getName())
                    .collect(Collectors.joining(", "));
            investment.setApprovedBy(approvers);
            
            investmentRepository.save(investment);
            
            // Create transaction
            createInvestmentTransaction(investment);
        }
    }

    private void createInvestmentTransaction(Investment investment) {
        Transaction transaction = Transaction.builder()
                .date(LocalDate.now())
                .type(Transaction.TransactionType.INVESTMENT)
                .description("Investment: " + investment.getName())
                .amount(investment.getAmountInvested())
                .direction(Transaction.TransactionDirection.OUT)
                .referenceId(investment.getId())
                .referenceType("INVESTMENT")
                .tenant(investment.getTenant())
                .build();
        transactionRepository.save(transaction);
    }

    // Get pending approvals for a specific member
    public List<InvestmentApprovalDto> getPendingApprovalsForMember(Long memberId) {
        return approvalRepository.findPendingApprovalsForMember(memberId).stream()
                .map(this::toApprovalDto)
                .collect(Collectors.toList());
    }

    // Count pending approvals for member (for badge/notification)
    public long countPendingApprovalsForMember(Long memberId) {
        return approvalRepository.countPendingApprovalsForMember(memberId);
    }

    @Transactional
    public InvestmentDto approveInvestment(Long id, Long tenantId, String approverName) {
        Investment investment = investmentRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Investment not found"));

        investment.setApprovalStatus(Investment.ApprovalStatus.APPROVED);
        investment.setDateInvested(LocalDate.now());
        investment.setApprovedBy(approverName);
        investment.setApprovedAt(LocalDateTime.now());
        investment = investmentRepository.save(investment);

        createInvestmentTransaction(investment);

        return toDto(investment);
    }

    @Transactional
    public InvestmentDto rejectInvestment(Long id, Long tenantId, String reason) {
        Investment investment = investmentRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Investment not found"));
        investment.setApprovalStatus(Investment.ApprovalStatus.REJECTED);
        investment.setRejectionReason(reason);
        investment = investmentRepository.save(investment);
        return toDto(investment);
    }

    @Transactional
    public InvestmentDto updateInvestment(Long id, InvestmentDto dto, Long tenantId) {
        Investment investment = investmentRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Investment not found"));

        investment.setName(dto.getName());
        investment.setCategory(Investment.InvestmentCategory.valueOf(dto.getCategory()));
        investment.setCurrentValue(dto.getCurrentValue());
        investment.setDescription(dto.getDescription());

        investment = investmentRepository.save(investment);
        return toDto(investment);
    }

    @Transactional
    public InvestmentDto updateInvestmentValue(Long id, Long tenantId, UpdateInvestmentValueRequest request) {
        Investment investment = investmentRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Investment not found"));
        
        if (investment.getStatus() != Investment.InvestmentStatus.ACTIVE) {
            throw new RuntimeException("Cannot update value of a non-active investment");
        }

        java.math.BigDecimal previousValue = investment.getCurrentValue();
        java.math.BigDecimal newValue = request.getNewValue();
        java.math.BigDecimal valueChange = newValue.subtract(previousValue);

        // Create value history entry
        createValueHistoryEntry(investment, newValue, previousValue, request.getNotes());
        
        // Update current value
        investment.setCurrentValue(newValue);
        investment = investmentRepository.save(investment);
        
        // Create GL transaction for value change (interest/returns)
        if (valueChange.compareTo(java.math.BigDecimal.ZERO) > 0) {
            // Value increased - interest/returns earned
            String description = String.format("Investment return: %s - %s", 
                    investment.getName(), 
                    request.getNotes() != null ? request.getNotes() : "Value appreciation");
            Transaction transaction = Transaction.builder()
                    .date(LocalDate.now())
                    .type(Transaction.TransactionType.INVESTMENT_RETURN)
                    .description(description)
                    .amount(valueChange)
                    .direction(Transaction.TransactionDirection.IN)
                    .referenceId(investment.getId())
                    .referenceType("INVESTMENT_RETURN")
                    .tenant(investment.getTenant())
                    .build();
            transactionRepository.save(transaction);
        } else if (valueChange.compareTo(java.math.BigDecimal.ZERO) < 0) {
            // Value decreased - loss recorded
            String description = String.format("Investment loss: %s - %s", 
                    investment.getName(), 
                    request.getNotes() != null ? request.getNotes() : "Value depreciation");
            Transaction transaction = Transaction.builder()
                    .date(LocalDate.now())
                    .type(Transaction.TransactionType.INVESTMENT_RETURN)
                    .description(description)
                    .amount(valueChange.abs())
                    .direction(Transaction.TransactionDirection.OUT)
                    .referenceId(investment.getId())
                    .referenceType("INVESTMENT_LOSS")
                    .tenant(investment.getTenant())
                    .build();
            transactionRepository.save(transaction);
        }
        
        return toDto(investment);
    }

    @Transactional
    public InvestmentDto sellInvestment(Long id, Long tenantId, SellInvestmentRequest request) {
        Investment investment = investmentRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Investment not found"));
        
        if (investment.getStatus() != Investment.InvestmentStatus.ACTIVE) {
            throw new RuntimeException("Investment is not active");
        }
        
        if (investment.getApprovalStatus() != Investment.ApprovalStatus.APPROVED) {
            throw new RuntimeException("Investment must be approved before selling");
        }

        String currentUser = getCurrentUsername();
        LocalDate soldDate = request.getSoldDate() != null ? LocalDate.parse(request.getSoldDate()) : LocalDate.now();
        
        investment.setStatus(Investment.InvestmentStatus.SOLD);
        investment.setSoldDate(soldDate);
        investment.setSoldAmount(request.getSoldAmount());
        investment.setSoldBy(currentUser);
        investment.setSoldNotes(request.getNotes());
        investment.setCurrentValue(request.getSoldAmount());
        
        investment = investmentRepository.save(investment);

        // Create value history entry for sale
        createValueHistoryEntry(investment, request.getSoldAmount(), investment.getCurrentValue(), "Investment sold: " + request.getNotes());

        // Create transaction for sale proceeds
        Transaction transaction = Transaction.builder()
                .date(soldDate)
                .type(Transaction.TransactionType.INVESTMENT)
                .description("Sale of Investment: " + investment.getName())
                .amount(request.getSoldAmount())
                .direction(Transaction.TransactionDirection.IN)
                .referenceId(investment.getId())
                .referenceType("INVESTMENT_SALE")
                .tenant(investment.getTenant())
                .build();
        transactionRepository.save(transaction);

        return toDto(investment);
    }

    @Transactional
    public InvestmentDto writeOffInvestment(Long id, Long tenantId, String notes) {
        Investment investment = investmentRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Investment not found"));
        
        if (investment.getStatus() != Investment.InvestmentStatus.ACTIVE) {
            throw new RuntimeException("Investment is not active");
        }

        String currentUser = getCurrentUsername();
        
        // Create value history entry for write-off
        createValueHistoryEntry(investment, java.math.BigDecimal.ZERO, investment.getCurrentValue(), "Investment written off: " + notes);
        
        investment.setStatus(Investment.InvestmentStatus.WRITTEN_OFF);
        investment.setSoldDate(LocalDate.now());
        investment.setSoldAmount(java.math.BigDecimal.ZERO);
        investment.setSoldBy(currentUser);
        investment.setSoldNotes(notes);
        investment.setCurrentValue(java.math.BigDecimal.ZERO);
        
        investment = investmentRepository.save(investment);
        
        return toDto(investmentRepository.findByIdAndTenantId(id, tenantId).orElse(investment));
    }

    public List<InvestmentValueHistoryDto> getValueHistory(Long investmentId, Long tenantId) {
        // Verify investment belongs to tenant
        investmentRepository.findByIdAndTenantId(investmentId, tenantId)
                .orElseThrow(() -> new RuntimeException("Investment not found"));
        
        return valueHistoryRepository.findByInvestmentIdOrderByValueDateAsc(investmentId).stream()
                .map(this::toHistoryDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public InvestmentDto topupInvestment(Long id, Long tenantId, Long memberId, TopupInvestmentRequest request) {
        Investment investment = investmentRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Investment not found"));
        
        if (investment.getStatus() != Investment.InvestmentStatus.ACTIVE) {
            throw new RuntimeException("Cannot add principal to a non-active investment");
        }
        
        if (investment.getApprovalStatus() != Investment.ApprovalStatus.APPROVED) {
            throw new RuntimeException("Investment must be approved before adding principal");
        }

        Member recordedBy = null;
        String recordedByName = getCurrentUsername();
        if (memberId != null) {
            recordedBy = memberRepository.findById(memberId).orElse(null);
            if (recordedBy != null) {
                recordedByName = recordedBy.getName();
            }
        }

        java.math.BigDecimal previousPrincipal = investment.getAmountInvested();
        java.math.BigDecimal newPrincipal = previousPrincipal.add(request.getAmount());
        LocalDate topupDate = request.getTopupDate() != null ? request.getTopupDate() : LocalDate.now();

        // Create topup record
        InvestmentTopup topup = InvestmentTopup.builder()
                .investment(investment)
                .amount(request.getAmount())
                .topupDate(topupDate)
                .previousPrincipal(previousPrincipal)
                .newPrincipal(newPrincipal)
                .notes(request.getNotes())
                .recordedBy(recordedBy)
                .recordedByName(recordedByName)
                .recordedAt(LocalDateTime.now())
                .build();
        investmentTopupRepository.save(topup);

        // Update investment's amountInvested (base amount)
        investment.setAmountInvested(newPrincipal);
        
        // Also increase currentValue by the same amount (top-up adds to the value)
        investment.setCurrentValue(investment.getCurrentValue().add(request.getAmount()));
        
        investment = investmentRepository.save(investment);

        // Create value history entry for the top-up
        createValueHistoryEntry(investment, investment.getCurrentValue(), 
                investment.getCurrentValue().subtract(request.getAmount()), 
                "Principal top-up: " + request.getAmount() + (request.getNotes() != null ? " - " + request.getNotes() : ""));

        // Create transaction for the top-up
        Transaction transaction = Transaction.builder()
                .date(topupDate)
                .type(Transaction.TransactionType.INVESTMENT)
                .description("Investment Top-up: " + investment.getName())
                .amount(request.getAmount())
                .direction(Transaction.TransactionDirection.OUT)
                .referenceId(investment.getId())
                .referenceType("INVESTMENT_TOPUP")
                .tenant(investment.getTenant())
                .build();
        transactionRepository.save(transaction);

        return toDto(investment);
    }

    public List<InvestmentTopupDto> getTopupHistory(Long investmentId, Long tenantId) {
        // Verify investment belongs to tenant
        investmentRepository.findByIdAndTenantId(investmentId, tenantId)
                .orElseThrow(() -> new RuntimeException("Investment not found"));
        
        return investmentTopupRepository.findByInvestmentIdOrderByTopupDateDesc(investmentId).stream()
                .map(this::toTopupDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteInvestment(Long id, Long tenantId) {
        Investment investment = investmentRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Investment not found"));
        
        // Delete value history first (child records)
        valueHistoryRepository.deleteByInvestmentId(id);
        
        // Delete the investment
        investmentRepository.delete(investment);
    }

    private void createValueHistoryEntry(Investment investment, java.math.BigDecimal newValue, java.math.BigDecimal previousValue, String notes) {
        String currentUser = getCurrentUsername();
        
        InvestmentValueHistory history = InvestmentValueHistory.builder()
                .investment(investment)
                .valueDate(LocalDate.now())
                .recordedValue(newValue)
                .previousValue(previousValue)
                .notes(notes)
                .recordedBy(currentUser)
                .build();
        
        valueHistoryRepository.save(history);
    }

    private String getCurrentUsername() {
        try {
            return SecurityContextHolder.getContext().getAuthentication().getName();
        } catch (Exception e) {
            return "system";
        }
    }

    private InvestmentDto toDto(Investment investment) {
        List<InvestmentApprovalDto> approvalDtos = investment.getApprovals() != null 
                ? investment.getApprovals().stream().map(this::toApprovalDto).collect(Collectors.toList())
                : new ArrayList<>();

        return InvestmentDto.builder()
                .id(investment.getId())
                .name(investment.getName())
                .category(investment.getCategory().name())
                .amountInvested(investment.getAmountInvested())
                .currentValue(investment.getCurrentValue())
                .approvalStatus(investment.getApprovalStatus().name())
                .status(investment.getStatus().name())
                .dateInvested(investment.getDateInvested() != null ? investment.getDateInvested().toString() : null)
                .description(investment.getDescription())
                .netGain(investment.getNetGain())
                .returnPercentage(investment.getReturnPercentage())
                .requiresApproval(investment.getRequiresApproval())
                .requiredApprovals(investment.getRequiredApprovals())
                .approvals(approvalDtos)
                .approvalCount(investment.getApprovalCount())
                .rejectionCount(investment.getRejectionCount())
                .pendingCount(investment.getPendingCount())
                .createdByMemberId(investment.getCreatedByMember() != null ? investment.getCreatedByMember().getId() : null)
                .createdByMemberName(investment.getCreatedByMember() != null ? investment.getCreatedByMember().getName() : null)
                .approvedBy(investment.getApprovedBy())
                .approvedAt(investment.getApprovedAt() != null ? investment.getApprovedAt().toString() : null)
                .rejectionReason(investment.getRejectionReason())
                .soldDate(investment.getSoldDate() != null ? investment.getSoldDate().toString() : null)
                .soldAmount(investment.getSoldAmount())
                .soldBy(investment.getSoldBy())
                .soldNotes(investment.getSoldNotes())
                .finalGain(investment.getFinalGain())
                .build();
    }

    private InvestmentApprovalDto toApprovalDto(InvestmentApproval approval) {
        return InvestmentApprovalDto.builder()
                .id(approval.getId())
                .investmentId(approval.getInvestment().getId())
                .investmentName(approval.getInvestment().getName())
                .approverId(approval.getApprover().getId())
                .approverName(approval.getApprover().getName())
                .decision(approval.getDecision().name())
                .decidedAt(approval.getDecidedAt() != null ? approval.getDecidedAt().toString() : null)
                .comments(approval.getComments())
                .build();
    }

    private InvestmentValueHistoryDto toHistoryDto(InvestmentValueHistory history) {
        return InvestmentValueHistoryDto.builder()
                .id(history.getId())
                .investmentId(history.getInvestment().getId())
                .valueDate(history.getValueDate().toString())
                .recordedValue(history.getRecordedValue())
                .previousValue(history.getPreviousValue())
                .valueChange(history.getValueChange())
                .percentageChange(history.getPercentageChange())
                .notes(history.getNotes())
                .recordedBy(history.getRecordedBy())
                .build();
    }

    private InvestmentTopupDto toTopupDto(InvestmentTopup topup) {
        InvestmentTopupDto dto = new InvestmentTopupDto();
        dto.setId(topup.getId());
        dto.setInvestmentId(topup.getInvestment().getId());
        dto.setAmount(topup.getAmount());
        dto.setTopupDate(topup.getTopupDate());
        dto.setPreviousPrincipal(topup.getPreviousPrincipal());
        dto.setNewPrincipal(topup.getNewPrincipal());
        dto.setNotes(topup.getNotes());
        dto.setRecordedById(topup.getRecordedBy() != null ? topup.getRecordedBy().getId() : null);
        dto.setRecordedByName(topup.getRecordedByName());
        dto.setRecordedAt(topup.getRecordedAt());
        return dto;
    }
}
