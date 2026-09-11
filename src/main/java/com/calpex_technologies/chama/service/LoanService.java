package com.calpex_technologies.chama.service;

import com.calpex_technologies.chama.dto.LoanDto;
import com.calpex_technologies.chama.entity.*;
import com.calpex_technologies.chama.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class LoanService {

    private final LoanRepository loanRepository;
    private final MemberRepository memberRepository;
    private final TenantRepository tenantRepository;
    private final TransactionRepository transactionRepository;
    private final LoanProductRepository loanProductRepository;
    private final LoanApprovalRepository loanApprovalRepository;
    private final LoanApprovalLevelRepository approvalLevelRepository;
    private final RoleRepository roleRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // ==================== GET METHODS ====================

    public List<LoanDto> getAllLoans(Long tenantId) {
        return loanRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<LoanDto> getMyLoans(Long tenantId, Long memberId) {
        return loanRepository.findByMemberIdOrderByCreatedAtDesc(memberId).stream()
                .filter(l -> l.getTenant().getId().equals(tenantId))
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<LoanDto> getPendingApprovals(Long tenantId, Long memberId) {
        // Get all loans pending approval
        List<Loan> pendingLoans = loanRepository.findAwaitingApprovalByTenantId(tenantId);
        
        // Filter to only loans where this member is the next approver
        return pendingLoans.stream()
                .filter(loan -> {
                    if (loan.getLoanProduct() == null) return false;
                    
                    int nextLevel = loan.getCurrentApprovalLevel() + 1;
                    return approvalLevelRepository.findByLoanProductIdAndLevel(loan.getLoanProduct().getId(), nextLevel)
                            .map(level -> {
                                // Check if this specific member is the approver
                                if (level.getMember() != null) {
                                    return level.getMember().getId().equals(memberId);
                                }
                                // Fallback to role-based check
                                Member member = memberRepository.findById(memberId).orElse(null);
                                if (member != null && member.getRole() != null && level.getRole() != null) {
                                    return level.getRole().getId().equals(member.getRole().getId());
                                }
                                return false;
                            })
                            .orElse(false);
                })
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public LoanDto getLoan(Long id, Long tenantId) {
        Loan loan = loanRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Loan not found"));
        return toDto(loan);
    }

    // ==================== LOAN APPLICATION ====================

    @Transactional
    public LoanDto applyForLoan(LoanDto dto, Long tenantId, Long applicantMemberId, boolean isAdmin) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        // Get the member the loan is for
        Member member = memberRepository.findByIdAndTenantId(dto.getMemberId(), tenantId)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        // Get the member applying (could be admin on behalf of member)
        Member appliedBy = memberRepository.findById(applicantMemberId)
                .orElseThrow(() -> new RuntimeException("Applicant not found"));

        // Non-admins can only apply for themselves
        if (!isAdmin && !member.getId().equals(applicantMemberId)) {
            throw new RuntimeException("You can only apply for loans for yourself");
        }

        // Get loan product
        LoanProduct product = null;
        BigDecimal interestRate = dto.getInterestRate();
        int termMonths = dto.getTermMonths();
        int requiredApprovalLevels = 1;

        if (dto.getLoanProductId() != null) {
            product = loanProductRepository.findByIdAndTenantId(dto.getLoanProductId(), tenantId)
                    .orElseThrow(() -> new RuntimeException("Loan product not found"));
            
            if (!product.isActive()) {
                throw new RuntimeException("This loan product is not active");
            }

            // Validate against product constraints
            if (product.getMinAmount() != null && dto.getPrincipal().compareTo(product.getMinAmount()) < 0) {
                throw new RuntimeException("Loan amount is below minimum of " + product.getMinAmount());
            }
            if (product.getMaxAmount() != null && dto.getPrincipal().compareTo(product.getMaxAmount()) > 0) {
                throw new RuntimeException("Loan amount exceeds maximum of " + product.getMaxAmount());
            }

            interestRate = product.getInterestRate();
            termMonths = product.getRepaymentPeriod();
            requiredApprovalLevels = product.isRequiresApproval() ? product.getApprovalLevels() : 0;
        }

        // Get guarantors
        List<Member> guarantors = new ArrayList<>();
        if (dto.getGuarantorIds() != null) {
            for (Long gId : dto.getGuarantorIds()) {
                Member g = memberRepository.findByIdAndTenantId(gId, tenantId).orElse(null);
                if (g != null && !g.getId().equals(member.getId())) {
                    guarantors.add(g);
                }
            }
        }

        // Check required guarantors
        if (product != null && product.getRequiredGuarantors() > 0 && guarantors.size() < product.getRequiredGuarantors()) {
            throw new RuntimeException("This loan requires at least " + product.getRequiredGuarantors() + " guarantor(s)");
        }

        // Calculate loan amounts
        BigDecimal principal = dto.getPrincipal();
        BigDecimal totalInterest = calculateInterest(principal, interestRate, termMonths, 
                product != null ? product.getInterestType() : LoanProduct.InterestType.REDUCING_BALANCE);
        BigDecimal totalAmount = principal.add(totalInterest);
        BigDecimal monthlyPayment = totalAmount.divide(BigDecimal.valueOf(termMonths), 2, RoundingMode.HALF_UP);

        Loan loan = Loan.builder()
                .tenant(tenant)
                .member(member)
                .loanProduct(product)
                .principal(principal)
                .interestRate(interestRate)
                .termMonths(termMonths)
                .totalInterest(totalInterest)
                .totalAmount(totalAmount)
                .monthlyPayment(monthlyPayment)
                .balanceOutstanding(totalAmount)
                .totalPaid(BigDecimal.ZERO)
                .totalPenalties(BigDecimal.ZERO)
                .status(requiredApprovalLevels > 0 ? Loan.LoanStatus.PENDING : Loan.LoanStatus.APPROVED)
                .currentApprovalLevel(0)
                .requiredApprovalLevels(requiredApprovalLevels)
                .appliedBy(appliedBy)
                .guarantors(guarantors)
                .applicationDate(LocalDate.now())
                .purpose(dto.getPurpose())
                .build();

        loan = loanRepository.save(loan);
        log.info("Loan application created: {} for member {} amount {}", loan.getId(), member.getName(), principal);

        return toDto(loan);
    }

    // ==================== APPROVAL WORKFLOW ====================

    @Transactional
    public LoanDto approveLoan(Long loanId, Long tenantId, Long approverId, String comments) {
        Loan loan = loanRepository.findByIdAndTenantId(loanId, tenantId)
                .orElseThrow(() -> new RuntimeException("Loan not found"));

        if (loan.getStatus() != Loan.LoanStatus.PENDING && loan.getStatus() != Loan.LoanStatus.PENDING_APPROVAL) {
            throw new RuntimeException("Loan is not pending approval");
        }

        Member approver = memberRepository.findById(approverId)
                .orElseThrow(() -> new RuntimeException("Approver not found"));

        // Verify approver has permission for this level
        int nextLevel = loan.getCurrentApprovalLevel() + 1;
        if (loan.getLoanProduct() != null) {
            LoanApprovalLevel levelConfig = approvalLevelRepository
                    .findByLoanProductIdAndLevel(loan.getLoanProduct().getId(), nextLevel)
                    .orElseThrow(() -> new RuntimeException("Approval level not configured"));

            // Check if this specific member is the designated approver
            boolean isAuthorized = false;
            if (levelConfig.getMember() != null) {
                isAuthorized = levelConfig.getMember().getId().equals(approverId);
            } else if (levelConfig.getRole() != null && approver.getRole() != null) {
                // Fallback to role-based check
                isAuthorized = approver.getRole().getId().equals(levelConfig.getRole().getId());
            }
            
            if (!isAuthorized) {
                throw new RuntimeException("You are not authorized to approve at this level");
            }
        }

        // Record the approval
        LoanApproval approval = LoanApproval.builder()
                .loan(loan)
                .level(nextLevel)
                .approver(approver)
                .approverRole(approver.getRole())
                .action(LoanApproval.ApprovalAction.APPROVED)
                .comments(comments)
                .actionDate(LocalDateTime.now())
                .build();
        loanApprovalRepository.save(approval);

        // Update loan
        loan.setCurrentApprovalLevel(nextLevel);
        
        if (nextLevel >= loan.getRequiredApprovalLevels()) {
            loan.setStatus(Loan.LoanStatus.APPROVED);
            loan.setApprovedDate(LocalDate.now());
            log.info("Loan {} fully approved", loanId);
        } else {
            loan.setStatus(Loan.LoanStatus.PENDING_APPROVAL);
            log.info("Loan {} approved at level {}, awaiting level {}", loanId, nextLevel, nextLevel + 1);
        }

        loan = loanRepository.save(loan);
        return toDto(loan);
    }

    @Transactional
    public LoanDto rejectLoan(Long loanId, Long tenantId, Long approverId, String reason) {
        Loan loan = loanRepository.findByIdAndTenantId(loanId, tenantId)
                .orElseThrow(() -> new RuntimeException("Loan not found"));

        if (loan.getStatus() != Loan.LoanStatus.PENDING && loan.getStatus() != Loan.LoanStatus.PENDING_APPROVAL) {
            throw new RuntimeException("Loan is not pending approval");
        }

        Member approver = memberRepository.findById(approverId)
                .orElseThrow(() -> new RuntimeException("Approver not found"));

        // Record the rejection
        int currentLevel = loan.getCurrentApprovalLevel() + 1;
        LoanApproval approval = LoanApproval.builder()
                .loan(loan)
                .level(currentLevel)
                .approver(approver)
                .approverRole(approver.getRole())
                .action(LoanApproval.ApprovalAction.REJECTED)
                .comments(reason)
                .actionDate(LocalDateTime.now())
                .build();
        loanApprovalRepository.save(approval);

        // Update loan
        loan.setStatus(Loan.LoanStatus.REJECTED);
        loan.setRejectionReason(reason);
        loan.setRejectedById(approverId);
        loan.setRejectedAt(LocalDateTime.now());

        loan = loanRepository.save(loan);
        log.info("Loan {} rejected by {}: {}", loanId, approver.getName(), reason);

        return toDto(loan);
    }

    // ==================== DISBURSEMENT ====================

    @Transactional
    public LoanDto disburseLoan(Long loanId, Long tenantId) {
        Loan loan = loanRepository.findByIdAndTenantId(loanId, tenantId)
                .orElseThrow(() -> new RuntimeException("Loan not found"));

        if (loan.getStatus() != Loan.LoanStatus.APPROVED) {
            throw new RuntimeException("Loan is not approved for disbursement");
        }

        loan.setStatus(Loan.LoanStatus.ACTIVE);
        loan.setDisbursedOn(LocalDate.now());
        loan.setNextDueDate(LocalDate.now().plusMonths(1));
        loan.setExpectedCompletionDate(LocalDate.now().plusMonths(loan.getTermMonths()));
        loan = loanRepository.save(loan);

        // Update member outstanding loan
        Member member = loan.getMember();
        BigDecimal outstanding = loanRepository.sumOutstandingByMemberId(member.getId());
        member.setOutstandingLoan(outstanding != null ? outstanding : BigDecimal.ZERO);
        memberRepository.save(member);

        // Create transaction - only record principal as outflow (interest is revenue when paid)
        Transaction transaction = Transaction.builder()
                .date(LocalDate.now())
                .type(Transaction.TransactionType.LOAN_DISBURSEMENT)
                .description("Loan disbursement to " + member.getName())
                .amount(loan.getPrincipal())
                .direction(Transaction.TransactionDirection.OUT)
                .member(member)
                .referenceId(loan.getId())
                .referenceType("LOAN")
                .tenant(loan.getTenant())
                .build();
        transactionRepository.save(transaction);

        log.info("Loan {} disbursed to {}, amount {}", loanId, member.getName(), loan.getPrincipal());
        return toDto(loan);
    }

    // ==================== REPAYMENT ====================

    @Transactional
    public LoanDto makeRepayment(Long loanId, BigDecimal amount, Long tenantId) {
        Loan loan = loanRepository.findByIdAndTenantId(loanId, tenantId)
                .orElseThrow(() -> new RuntimeException("Loan not found"));

        if (loan.getStatus() != Loan.LoanStatus.ACTIVE && loan.getStatus() != Loan.LoanStatus.OVERDUE) {
            throw new RuntimeException("Loan is not active");
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Payment amount must be greater than 0");
        }

        // Apply payment
        BigDecimal newPaid = loan.getTotalPaid().add(amount);
        BigDecimal newBalance = loan.getTotalAmount().subtract(newPaid);
        
        loan.setTotalPaid(newPaid);
        loan.setBalanceOutstanding(newBalance.max(BigDecimal.ZERO));
        loan.setLastPaymentDate(LocalDate.now());

        // Check if loan is cleared
        if (newBalance.compareTo(BigDecimal.ZERO) <= 0) {
            loan.setStatus(Loan.LoanStatus.CLEARED);
            loan.setBalanceOutstanding(BigDecimal.ZERO);
            loan.setClearedDate(LocalDate.now());
            log.info("Loan {} fully cleared", loanId);
        } else {
            // Update next due date
            loan.setNextDueDate(LocalDate.now().plusMonths(1));
            // Reset overdue status if was overdue
            if (loan.getStatus() == Loan.LoanStatus.OVERDUE) {
                loan.setStatus(Loan.LoanStatus.ACTIVE);
            }
        }
        
        loan = loanRepository.save(loan);

        // Update member outstanding loan total
        Member member = loan.getMember();
        BigDecimal memberOutstanding = loanRepository.sumOutstandingByMemberId(member.getId());
        member.setOutstandingLoan(memberOutstanding != null ? memberOutstanding : BigDecimal.ZERO);
        memberRepository.save(member);

        // Create transaction
        Transaction transaction = Transaction.builder()
                .date(LocalDate.now())
                .type(Transaction.TransactionType.LOAN_REPAYMENT)
                .description("Loan repayment from " + member.getName())
                .amount(amount)
                .direction(Transaction.TransactionDirection.IN)
                .member(member)
                .referenceId(loan.getId())
                .referenceType("LOAN")
                .tenant(loan.getTenant())
                .build();
        transactionRepository.save(transaction);

        return toDto(loan);
    }

    // ==================== HELPER METHODS ====================

    private BigDecimal calculateInterest(BigDecimal principal, BigDecimal rate, int termMonths, 
                                         LoanProduct.InterestType interestType) {
        if (interestType == LoanProduct.InterestType.FLAT) {
            // Simple flat interest: Principal * Rate/100 * Years
            BigDecimal years = BigDecimal.valueOf(termMonths).divide(BigDecimal.valueOf(12), 4, RoundingMode.HALF_UP);
            return principal.multiply(rate.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)).multiply(years);
        } else {
            // Reducing balance - approximation for display purposes
            // In practice, interest is calculated monthly on outstanding balance
            BigDecimal monthlyRate = rate.divide(BigDecimal.valueOf(1200), 6, RoundingMode.HALF_UP);
            BigDecimal totalInterest = BigDecimal.ZERO;
            BigDecimal balance = principal;
            BigDecimal payment = principal.divide(BigDecimal.valueOf(termMonths), 2, RoundingMode.HALF_UP);
            
            for (int i = 0; i < termMonths; i++) {
                BigDecimal monthInterest = balance.multiply(monthlyRate).setScale(2, RoundingMode.HALF_UP);
                totalInterest = totalInterest.add(monthInterest);
                balance = balance.subtract(payment).max(BigDecimal.ZERO);
            }
            return totalInterest;
        }
    }

    private LoanDto toDto(Loan loan) {
        List<LoanApproval> approvals = loanApprovalRepository.findByLoanIdOrderByLevelAsc(loan.getId());
        
        List<LoanDto.LoanApprovalDto> approvalDtos = approvals.stream()
                .map(a -> LoanDto.LoanApprovalDto.builder()
                        .level(a.getLevel())
                        .levelName(getLevelName(loan, a.getLevel()))
                        .approverName(a.getApprover().getName())
                        .approverRole(a.getApproverRole() != null ? a.getApproverRole().getName() : null)
                        .action(a.getAction().name())
                        .comments(a.getComments())
                        .actionDate(a.getActionDate() != null ? a.getActionDate().format(DATETIME_FORMATTER) : null)
                        .build())
                .collect(Collectors.toList());

        // Get pending approver name (member name or role name)
        String pendingApprovalRole = null;
        if ((loan.getStatus() == Loan.LoanStatus.PENDING || loan.getStatus() == Loan.LoanStatus.PENDING_APPROVAL)
                && loan.getLoanProduct() != null) {
            int nextLevel = loan.getCurrentApprovalLevel() + 1;
            pendingApprovalRole = approvalLevelRepository.findByLoanProductIdAndLevel(loan.getLoanProduct().getId(), nextLevel)
                    .map(level -> {
                        // Prefer member name, fallback to role name
                        if (level.getMember() != null) {
                            return level.getMember().getName();
                        } else if (level.getRole() != null) {
                            return level.getRole().getName();
                        }
                        return null;
                    })
                    .orElse(null);
        }

        LoanDto.LoanDtoBuilder builder = LoanDto.builder()
                .id(loan.getId())
                .memberId(loan.getMember().getId())
                .memberName(loan.getMember().getName())
                .memberNo(loan.getMember().getMemberNo())
                .principal(loan.getPrincipal())
                .interestRate(loan.getInterestRate())
                .termMonths(loan.getTermMonths())
                .totalInterest(loan.getTotalInterest())
                .totalAmount(loan.getTotalAmount())
                .monthlyPayment(loan.getMonthlyPayment())
                .balanceOutstanding(loan.getBalanceOutstanding())
                .totalPaid(loan.getTotalPaid())
                .totalPenalties(loan.getTotalPenalties())
                .status(loan.getStatus().name())
                .currentApprovalLevel(loan.getCurrentApprovalLevel())
                .requiredApprovalLevels(loan.getRequiredApprovalLevels())
                .pendingApprovalRole(pendingApprovalRole)
                .approvalHistory(approvalDtos)
                .guarantorIds(loan.getGuarantors().stream().map(Member::getId).collect(Collectors.toList()))
                .guarantorNames(loan.getGuarantors().stream().map(Member::getName).collect(Collectors.toList()))
                .guarantors(loan.getGuarantors().stream().map(Member::getName).collect(Collectors.toList()))
                .applicationDate(loan.getApplicationDate() != null ? loan.getApplicationDate().format(DATE_FORMATTER) : null)
                .purpose(loan.getPurpose())
                .approvedDate(loan.getApprovedDate() != null ? loan.getApprovedDate().format(DATE_FORMATTER) : null)
                .disbursedOn(loan.getDisbursedOn() != null ? loan.getDisbursedOn().format(DATE_FORMATTER) : null)
                .expectedCompletionDate(loan.getExpectedCompletionDate() != null ? loan.getExpectedCompletionDate().format(DATE_FORMATTER) : null)
                .nextDueDate(loan.getNextDueDate() != null ? loan.getNextDueDate().format(DATE_FORMATTER) : null)
                .lastPaymentDate(loan.getLastPaymentDate() != null ? loan.getLastPaymentDate().format(DATE_FORMATTER) : null)
                .clearedDate(loan.getClearedDate() != null ? loan.getClearedDate().format(DATE_FORMATTER) : null)
                .rejectionReason(loan.getRejectionReason());

        if (loan.getLoanProduct() != null) {
            builder.loanProductId(loan.getLoanProduct().getId())
                   .loanProductName(loan.getLoanProduct().getName());
        }

        if (loan.getAppliedBy() != null) {
            builder.appliedById(loan.getAppliedBy().getId())
                   .appliedByName(loan.getAppliedBy().getName());
        }

        if (loan.getRejectedById() != null) {
            memberRepository.findById(loan.getRejectedById())
                    .ifPresent(m -> builder.rejectedByName(m.getName()));
            if (loan.getRejectedAt() != null) {
                builder.rejectedAt(loan.getRejectedAt().format(DATETIME_FORMATTER));
            }
        }

        return builder.build();
    }

    private String getLevelName(Loan loan, int level) {
        if (loan.getLoanProduct() != null) {
            return approvalLevelRepository.findByLoanProductIdAndLevel(loan.getLoanProduct().getId(), level)
                    .map(LoanApprovalLevel::getLevelName)
                    .orElse("Level " + level);
        }
        return "Level " + level;
    }
}
