package com.calpex_technologies.chama.service;

import com.calpex_technologies.chama.dto.LoanDto;
import com.calpex_technologies.chama.dto.LoanEligibilityDto;
import com.calpex_technologies.chama.dto.LoanIncomeSummaryDto;
import com.calpex_technologies.chama.dto.LoanPaymentDto;
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
    private final ContributionRepository contributionRepository;
    private final LoanPaymentRepository loanPaymentRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int DEFAULT_PAGE_SIZE = 10;

    // ==================== GET METHODS ====================

    public List<LoanDto> getAllLoans(Long tenantId) {
        return loanRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }
    
    /**
     * Get all loans with pagination.
     */
    public PageResponse<LoanDto> getAllLoansPaginated(Long tenantId, String status, int page, int size) {
        if (size <= 0) size = DEFAULT_PAGE_SIZE;
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        
        Page<Loan> loanPage;
        
        if (status != null && !status.equalsIgnoreCase("ALL") && !status.trim().isEmpty()) {
            Loan.LoanStatus loanStatus = Loan.LoanStatus.valueOf(status.toUpperCase());
            loanPage = loanRepository.findByTenantIdAndStatus(tenantId, loanStatus, pageable);
        } else {
            loanPage = loanRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable);
        }
        
        Page<LoanDto> dtoPage = loanPage.map(this::toDto);
        return PageResponse.from(dtoPage);
    }
    
    /**
     * Get my loans with pagination.
     */
    public PageResponse<LoanDto> getMyLoansPaginated(Long tenantId, Long memberId, int page, int size) {
        if (size <= 0) size = DEFAULT_PAGE_SIZE;
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        
        Page<Loan> loanPage = loanRepository.findByMemberIdOrderByCreatedAtDesc(memberId, pageable);
        Page<LoanDto> dtoPage = loanPage.map(this::toDto);
        return PageResponse.from(dtoPage);
    }

    public List<LoanDto> getMyLoans(Long tenantId, Long memberId) {
        return loanRepository.findByMemberIdOrderByCreatedAtDesc(memberId).stream()
                .filter(l -> l.getTenant().getId().equals(tenantId))
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<LoanDto> getPendingApprovals(Long tenantId, Long memberId) {
        // Get all loans pending approval (includes PENDING_GUARANTORS, PENDING, PENDING_APPROVAL)
        List<Loan> pendingLoans = loanRepository.findAwaitingApprovalByTenantId(tenantId);
        
        // Filter to only loans where this member is a pending guarantor OR next approver
        return pendingLoans.stream()
                .filter(loan -> {
                    // Check if member is a pending guarantor for this loan (Phase 1)
                    if (loan.getStatus() == Loan.LoanStatus.PENDING_GUARANTORS) {
                        // Check if this member is in the guarantors list and hasn't approved yet
                        boolean isGuarantor = loan.getGuarantors().stream()
                                .anyMatch(g -> g.getId().equals(memberId));
                        if (isGuarantor) {
                            // Check if they've already approved as guarantor
                            boolean alreadyApproved = loanApprovalRepository.existsGuarantorApproval(loan.getId(), memberId);
                            if (!alreadyApproved) {
                                return true; // Show this loan - member is pending guarantor
                            }
                        }
                    }
                    
                    // Check if member is the next approver (Phase 2 - sequential approval)
                    if (loan.getStatus() == Loan.LoanStatus.PENDING || 
                        loan.getStatus() == Loan.LoanStatus.PENDING_APPROVAL) {
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
                    }
                    
                    return false;
                })
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public LoanDto getLoan(Long id, Long tenantId) {
        Loan loan = loanRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Loan not found"));
        return toDto(loan);
    }

    // ==================== LOAN ELIGIBILITY CHECK ====================

    /**
     * Check loan eligibility based on member and guarantors' contributions.
     * Returns a DTO with contribution details and eligibility status.
     */
    public LoanEligibilityDto checkLoanEligibility(Long tenantId, Long memberId, List<Long> guarantorIds, BigDecimal loanAmount) {
        BigDecimal applicantContributions = contributionRepository.sumPaidAmountByMemberId(memberId);
        if (applicantContributions == null) applicantContributions = BigDecimal.ZERO;
        
        BigDecimal guarantorContributions = BigDecimal.ZERO;
        if (guarantorIds != null) {
            for (Long gId : guarantorIds) {
                if (!gId.equals(memberId)) { // Don't count member's contributions twice
                    BigDecimal gContributions = contributionRepository.sumPaidAmountByMemberId(gId);
                    if (gContributions != null) {
                        guarantorContributions = guarantorContributions.add(gContributions);
                    }
                }
            }
        }
        
        BigDecimal totalContributions = applicantContributions.add(guarantorContributions);
        boolean eligible = totalContributions.compareTo(loanAmount) >= 0;
        BigDecimal shortfall = eligible ? BigDecimal.ZERO : loanAmount.subtract(totalContributions);
        
        return LoanEligibilityDto.builder()
                .applicantContributions(applicantContributions)
                .guarantorContributions(guarantorContributions)
                .totalContributions(totalContributions)
                .loanAmount(loanAmount)
                .eligible(eligible)
                .shortfall(shortfall)
                .build();
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
        int requiredGuarantors = 0;
        int requiredApprovalLevels = 0;

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
            requiredGuarantors = product.getRequiredGuarantors() != null ? product.getRequiredGuarantors() : 0;
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

        // Validate loan eligibility based on contributions (when a loan product is selected)
        // Rule: Applicant's contributions (+ guarantors' contributions if required) must be >= loan amount
        BigDecimal principal = dto.getPrincipal();
        
        if (product != null) {
            BigDecimal applicantContributions = contributionRepository.sumPaidAmountByMemberId(member.getId());
            if (applicantContributions == null) applicantContributions = BigDecimal.ZERO;
            
            BigDecimal guarantorContributions = BigDecimal.ZERO;
            if (product.getRequiredGuarantors() > 0) {
                // Include guarantor contributions only if guarantors are required
                for (Member g : guarantors) {
                    BigDecimal gContributions = contributionRepository.sumPaidAmountByMemberId(g.getId());
                    if (gContributions != null) {
                        guarantorContributions = guarantorContributions.add(gContributions);
                    }
                }
            }
            
            BigDecimal totalAvailableContributions = applicantContributions.add(guarantorContributions);
            
            if (totalAvailableContributions.compareTo(principal) < 0) {
                if (product.getRequiredGuarantors() > 0) {
                    throw new RuntimeException(
                        String.format("Insufficient contributions to secure this loan. " +
                            "Your contributions: KES %,.2f, Guarantors' contributions: KES %,.2f, Total: KES %,.2f. " +
                            "Loan amount requested: KES %,.2f. Please select more guarantors or reduce the loan amount.",
                            applicantContributions, guarantorContributions, totalAvailableContributions, principal)
                    );
                } else {
                    throw new RuntimeException(
                        String.format("Insufficient contributions to secure this loan. " +
                            "Your contributions: KES %,.2f. Loan amount requested: KES %,.2f. " +
                            "Please reduce the loan amount.",
                            applicantContributions, principal)
                    );
                }
            }
        }
        BigDecimal totalInterest = calculateInterest(principal, interestRate, termMonths, 
                product != null ? product.getInterestType() : LoanProduct.InterestType.REDUCING_BALANCE);
        
        // Calculate processing fee if enabled
        BigDecimal processingFee = BigDecimal.ZERO;
        LoanProduct.ProcessingFeeDeduction processingFeeDeduction = null;
        BigDecimal netDisbursementAmount = principal;
        BigDecimal totalAmount = principal.add(totalInterest);
        
        if (product != null && product.isProcessingFeeEnabled() && product.getProcessingFeeValue() != null) {
            processingFeeDeduction = product.getProcessingFeeDeduction();
            
            if (product.getProcessingFeeType() == LoanProduct.ProcessingFeeType.FIXED) {
                processingFee = product.getProcessingFeeValue();
            } else {
                // Percentage of principal
                processingFee = principal.multiply(product.getProcessingFeeValue())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            }
            
            if (processingFeeDeduction == LoanProduct.ProcessingFeeDeduction.UPFRONT) {
                // Deduct from disbursement - member receives less
                netDisbursementAmount = principal.subtract(processingFee);
            } else {
                // Add to repayment - member pays more
                totalAmount = totalAmount.add(processingFee);
            }
        }
        
        BigDecimal monthlyPayment = totalAmount.divide(BigDecimal.valueOf(termMonths), 2, RoundingMode.HALF_UP);

        // Determine initial status based on workflow requirements
        // Phase 1: Guarantors (if any) -> Phase 2: Approvers (if any) -> APPROVED
        Loan.LoanStatus initialStatus;
        if (requiredGuarantors > 0 && guarantors.size() >= requiredGuarantors) {
            // Has guarantors that need to approve first
            initialStatus = Loan.LoanStatus.PENDING_GUARANTORS;
        } else if (requiredApprovalLevels > 0) {
            // No guarantors needed, but has approvers
            initialStatus = Loan.LoanStatus.PENDING;
        } else {
            // No approval workflow required
            initialStatus = Loan.LoanStatus.APPROVED;
        }

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
                .processingFee(processingFee)
                .processingFeePaid(BigDecimal.ZERO)
                .netDisbursementAmount(netDisbursementAmount)
                .processingFeeDeductionType(processingFeeDeduction)
                .status(initialStatus)
                .requiredGuarantors(requiredGuarantors)
                .guarantorsApprovedCount(0)
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

    /**
     * Approve loan as a guarantor (Phase 1 - parallel approval)
     */
    @Transactional
    public LoanDto approveAsGuarantor(Long loanId, Long tenantId, Long guarantorId, String comments) {
        Loan loan = loanRepository.findByIdAndTenantId(loanId, tenantId)
                .orElseThrow(() -> new RuntimeException("Loan not found"));

        if (loan.getStatus() != Loan.LoanStatus.PENDING_GUARANTORS) {
            throw new RuntimeException("Loan is not awaiting guarantor approval");
        }

        Member guarantor = memberRepository.findById(guarantorId)
                .orElseThrow(() -> new RuntimeException("Guarantor not found"));

        // Verify this member is a guarantor for this loan
        boolean isGuarantor = loan.getGuarantors().stream()
                .anyMatch(g -> g.getId().equals(guarantorId));
        if (!isGuarantor) {
            throw new RuntimeException("You are not a guarantor for this loan");
        }

        // Check if already approved
        if (loanApprovalRepository.existsGuarantorApproval(loanId, guarantorId)) {
            throw new RuntimeException("You have already approved this loan as guarantor");
        }

        // Record the guarantor approval
        LoanApproval approval = LoanApproval.builder()
                .loan(loan)
                .approvalType(LoanApproval.ApprovalType.GUARANTOR)
                .level(null) // Guarantors don't have levels
                .approver(guarantor)
                .approverRole(guarantor.getRole())
                .action(LoanApproval.ApprovalAction.APPROVED)
                .comments(comments)
                .actionDate(LocalDateTime.now())
                .build();
        loanApprovalRepository.save(approval);

        // Update guarantor count
        loan.setGuarantorsApprovedCount(loan.getGuarantorsApprovedCount() + 1);

        // Check if all guarantors have approved
        if (loan.areAllGuarantorsApproved()) {
            // Move to Phase 2 (approvers) or APPROVED if no approvers needed
            if (loan.getRequiredApprovalLevels() > 0) {
                loan.setStatus(Loan.LoanStatus.PENDING);
                log.info("Loan {} - all guarantors approved, moving to approver phase", loanId);
            } else {
                loan.setStatus(Loan.LoanStatus.APPROVED);
                loan.setApprovedDate(LocalDate.now());
                log.info("Loan {} fully approved (no approvers required)", loanId);
            }
        } else {
            log.info("Loan {} - guarantor {} approved ({}/{})", loanId, guarantor.getName(), 
                    loan.getGuarantorsApprovedCount(), loan.getRequiredGuarantors());
        }

        loan = loanRepository.save(loan);
        return toDto(loan);
    }

    /**
     * Approve loan as an approver (Phase 2 - sequential approval)
     */
    @Transactional
    public LoanDto approveLoan(Long loanId, Long tenantId, Long approverId, String comments) {
        Loan loan = loanRepository.findByIdAndTenantId(loanId, tenantId)
                .orElseThrow(() -> new RuntimeException("Loan not found"));

        if (loan.getStatus() != Loan.LoanStatus.PENDING && loan.getStatus() != Loan.LoanStatus.PENDING_APPROVAL) {
            throw new RuntimeException("Loan is not pending approver approval");
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
                .approvalType(LoanApproval.ApprovalType.APPROVER)
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

    /**
     * Reject loan (can be by guarantor or approver)
     */
    @Transactional
    public LoanDto rejectLoan(Long loanId, Long tenantId, Long rejecterId, String reason, boolean asGuarantor) {
        Loan loan = loanRepository.findByIdAndTenantId(loanId, tenantId)
                .orElseThrow(() -> new RuntimeException("Loan not found"));

        Member rejecter = memberRepository.findById(rejecterId)
                .orElseThrow(() -> new RuntimeException("Rejecter not found"));

        LoanApproval.ApprovalType approvalType;
        Integer level = null;

        if (asGuarantor) {
            if (loan.getStatus() != Loan.LoanStatus.PENDING_GUARANTORS) {
                throw new RuntimeException("Loan is not awaiting guarantor approval");
            }
            // Verify this member is a guarantor
            boolean isGuarantor = loan.getGuarantors().stream()
                    .anyMatch(g -> g.getId().equals(rejecterId));
            if (!isGuarantor) {
                throw new RuntimeException("You are not a guarantor for this loan");
            }
            approvalType = LoanApproval.ApprovalType.GUARANTOR;
        } else {
            if (loan.getStatus() != Loan.LoanStatus.PENDING && loan.getStatus() != Loan.LoanStatus.PENDING_APPROVAL) {
                throw new RuntimeException("Loan is not pending approval");
            }
            approvalType = LoanApproval.ApprovalType.APPROVER;
            level = loan.getCurrentApprovalLevel() + 1;
        }

        // Record the rejection
        LoanApproval approval = LoanApproval.builder()
                .loan(loan)
                .approvalType(approvalType)
                .level(level)
                .approver(rejecter)
                .approverRole(rejecter.getRole())
                .action(LoanApproval.ApprovalAction.REJECTED)
                .comments(reason)
                .actionDate(LocalDateTime.now())
                .build();
        loanApprovalRepository.save(approval);

        // Update loan
        loan.setStatus(Loan.LoanStatus.REJECTED);
        loan.setRejectionReason(reason);
        loan.setRejectedById(rejecterId);
        loan.setRejectedAt(LocalDateTime.now());

        loan = loanRepository.save(loan);
        log.info("Loan {} rejected by {} (as {}): {}", loanId, rejecter.getName(), 
                asGuarantor ? "guarantor" : "approver", reason);

        return toDto(loan);
    }

    /**
     * Legacy reject method for backward compatibility
     */
    @Transactional
    public LoanDto rejectLoan(Long loanId, Long tenantId, Long approverId, String reason) {
        Loan loan = loanRepository.findByIdAndTenantId(loanId, tenantId)
                .orElseThrow(() -> new RuntimeException("Loan not found"));
        
        // Determine if rejecting as guarantor or approver based on loan status
        boolean asGuarantor = loan.getStatus() == Loan.LoanStatus.PENDING_GUARANTORS;
        return rejectLoan(loanId, tenantId, approverId, reason, asGuarantor);
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
        
        // If processing fee is upfront, mark it as paid now
        if (loan.getProcessingFeeDeductionType() == LoanProduct.ProcessingFeeDeduction.UPFRONT 
                && loan.getProcessingFee() != null 
                && loan.getProcessingFee().compareTo(BigDecimal.ZERO) > 0) {
            loan.setProcessingFeePaid(loan.getProcessingFee());
        }
        
        loan = loanRepository.save(loan);

        // Update member outstanding loan
        Member member = loan.getMember();
        BigDecimal outstanding = loanRepository.sumOutstandingByMemberId(member.getId());
        member.setOutstandingLoan(outstanding != null ? outstanding : BigDecimal.ZERO);
        memberRepository.save(member);

        // Create transaction for disbursement - use net amount (after upfront fee deduction)
        BigDecimal disbursementAmount = loan.getNetDisbursementAmount() != null 
                ? loan.getNetDisbursementAmount() 
                : loan.getPrincipal();
        
        Transaction disbursementTx = Transaction.builder()
                .date(LocalDate.now())
                .type(Transaction.TransactionType.LOAN_DISBURSEMENT)
                .description("Loan disbursement to " + member.getName())
                .amount(disbursementAmount)
                .direction(Transaction.TransactionDirection.OUT)
                .member(member)
                .referenceId(loan.getId())
                .referenceType("LOAN")
                .tenant(loan.getTenant())
                .build();
        transactionRepository.save(disbursementTx);

        // If upfront processing fee, record it as revenue (income)
        if (loan.getProcessingFeeDeductionType() == LoanProduct.ProcessingFeeDeduction.UPFRONT 
                && loan.getProcessingFee() != null 
                && loan.getProcessingFee().compareTo(BigDecimal.ZERO) > 0) {
            Transaction feeTx = Transaction.builder()
                    .date(LocalDate.now())
                    .type(Transaction.TransactionType.PROCESSING_FEE)
                    .description("Loan processing fee from " + member.getName())
                    .amount(loan.getProcessingFee())
                    .direction(Transaction.TransactionDirection.IN)
                    .member(member)
                    .referenceId(loan.getId())
                    .referenceType("LOAN")
                    .tenant(loan.getTenant())
                    .build();
            transactionRepository.save(feeTx);
            log.info("Processing fee of {} recorded for loan {}", loan.getProcessingFee(), loanId);
        }

        log.info("Loan {} disbursed to {}, net amount {}", loanId, member.getName(), disbursementAmount);
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

        // Track processing fee payment for ON_REPAYMENT type
        // Processing fee is paid proportionally with each repayment
        BigDecimal processingFeePaidThisTime = BigDecimal.ZERO;
        if (loan.getProcessingFeeDeductionType() == LoanProduct.ProcessingFeeDeduction.ON_REPAYMENT 
                && loan.getProcessingFee() != null 
                && loan.getProcessingFee().compareTo(BigDecimal.ZERO) > 0
                && loan.getProcessingFeePaid().compareTo(loan.getProcessingFee()) < 0) {
            
            // Calculate proportion of payment that goes to processing fee
            BigDecimal feeRemaining = loan.getProcessingFee().subtract(loan.getProcessingFeePaid());
            BigDecimal totalRemaining = loan.getTotalAmount().subtract(loan.getTotalPaid().subtract(amount));
            
            if (totalRemaining.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal feeRatio = loan.getProcessingFee().divide(loan.getTotalAmount(), 6, RoundingMode.HALF_UP);
                processingFeePaidThisTime = amount.multiply(feeRatio).setScale(2, RoundingMode.HALF_UP);
                processingFeePaidThisTime = processingFeePaidThisTime.min(feeRemaining); // Don't overpay
            }
            
            loan.setProcessingFeePaid(loan.getProcessingFeePaid().add(processingFeePaidThisTime));
        }

        // Check if loan is cleared
        if (newBalance.compareTo(BigDecimal.ZERO) <= 0) {
            loan.setStatus(Loan.LoanStatus.CLEARED);
            loan.setBalanceOutstanding(BigDecimal.ZERO);
            loan.setClearedDate(LocalDate.now());
            
            // Ensure processing fee is fully marked as paid
            if (loan.getProcessingFee() != null) {
                loan.setProcessingFeePaid(loan.getProcessingFee());
            }
            
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

        // Create repayment transaction (principal portion only)
        // Calculate interest portion of this payment
        BigDecimal interestPortion = BigDecimal.ZERO;
        BigDecimal principalPortion = amount;
        
        if (loan.getTotalInterest() != null && loan.getTotalInterest().compareTo(BigDecimal.ZERO) > 0 
                && loan.getTotalAmount().compareTo(BigDecimal.ZERO) > 0) {
            // Calculate what portion of the payment is interest vs principal
            // Interest ratio = totalInterest / totalAmount
            BigDecimal interestRatio = loan.getTotalInterest().divide(loan.getTotalAmount(), 6, RoundingMode.HALF_UP);
            interestPortion = amount.multiply(interestRatio).setScale(2, RoundingMode.HALF_UP);
            principalPortion = amount.subtract(interestPortion);
        }
        
        // Record principal repayment
        Transaction transaction = Transaction.builder()
                .date(LocalDate.now())
                .type(Transaction.TransactionType.LOAN_REPAYMENT)
                .description("Loan principal repayment from " + member.getName())
                .amount(principalPortion)
                .direction(Transaction.TransactionDirection.IN)
                .member(member)
                .referenceId(loan.getId())
                .referenceType("LOAN")
                .tenant(loan.getTenant())
                .build();
        transactionRepository.save(transaction);

        // Record interest portion as income
        if (interestPortion.compareTo(BigDecimal.ZERO) > 0) {
            Transaction interestTx = Transaction.builder()
                    .date(LocalDate.now())
                    .type(Transaction.TransactionType.LOAN_INTEREST)
                    .description("Loan interest income from " + member.getName())
                    .amount(interestPortion)
                    .direction(Transaction.TransactionDirection.IN)
                    .member(member)
                    .referenceId(loan.getId())
                    .referenceType("LOAN")
                    .tenant(loan.getTenant())
                    .build();
            transactionRepository.save(interestTx);
        }

        // Record processing fee portion as separate revenue transaction (ON_REPAYMENT type)
        if (processingFeePaidThisTime.compareTo(BigDecimal.ZERO) > 0) {
            Transaction feeTx = Transaction.builder()
                    .date(LocalDate.now())
                    .type(Transaction.TransactionType.PROCESSING_FEE)
                    .description("Loan processing fee (repayment) from " + member.getName())
                    .amount(processingFeePaidThisTime)
                    .direction(Transaction.TransactionDirection.IN)
                    .member(member)
                    .referenceId(loan.getId())
                    .referenceType("LOAN")
                    .tenant(loan.getTenant())
                    .build();
            transactionRepository.save(feeTx);
        }

        return toDto(loan);
    }

    // ==================== PAYMENT VERIFICATION WORKFLOW ====================

    /**
     * Record a payment that needs verification (user-initiated).
     * The payment starts as PENDING and must be verified by admin.
     */
    @Transactional
    public LoanPaymentDto recordPaymentForVerification(Long loanId, BigDecimal amount, String paymentMethod,
                                                        String transactionReference, String notes,
                                                        Long tenantId, Long recordedByMemberId) {
        Loan loan = loanRepository.findByIdAndTenantId(loanId, tenantId)
                .orElseThrow(() -> new RuntimeException("Loan not found"));

        if (loan.getStatus() != Loan.LoanStatus.ACTIVE && loan.getStatus() != Loan.LoanStatus.OVERDUE) {
            throw new RuntimeException("Loan is not active");
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Payment amount must be greater than 0");
        }

        Member recordedBy = memberRepository.findById(recordedByMemberId)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        LoanPayment payment = LoanPayment.builder()
                .loan(loan)
                .member(loan.getMember())
                .amount(amount)
                .paymentDate(LocalDate.now())
                .paymentMethod(paymentMethod)
                .transactionReference(transactionReference)
                .notes(notes)
                .status(LoanPayment.PaymentStatus.PENDING)
                .recordedBy(recordedBy)
                .recordedAt(LocalDateTime.now())
                .tenant(loan.getTenant())
                .build();

        payment = loanPaymentRepository.save(payment);
        log.info("Payment of {} recorded for loan {} by {} - awaiting verification", amount, loanId, recordedBy.getName());

        return toPaymentDto(payment);
    }

    /**
     * Verify a pending payment (admin action).
     * This applies the payment to the loan balance and creates transactions.
     */
    @Transactional
    public LoanPaymentDto verifyPayment(Long paymentId, Long tenantId, Long verifiedByMemberId) {
        LoanPayment payment = loanPaymentRepository.findByIdAndTenantId(paymentId, tenantId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        if (payment.getStatus() != LoanPayment.PaymentStatus.PENDING) {
            throw new RuntimeException("Payment is not pending verification");
        }

        Member verifiedBy = memberRepository.findById(verifiedByMemberId)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        // Mark payment as verified
        payment.setStatus(LoanPayment.PaymentStatus.VERIFIED);
        payment.setVerifiedBy(verifiedBy);
        payment.setVerifiedAt(LocalDateTime.now());
        loanPaymentRepository.save(payment);

        // Now apply the payment to the loan (same logic as makeRepayment)
        Loan loan = payment.getLoan();
        BigDecimal amount = payment.getAmount();

        // Apply payment
        BigDecimal newPaid = loan.getTotalPaid().add(amount);
        BigDecimal newBalance = loan.getTotalAmount().subtract(newPaid);
        
        loan.setTotalPaid(newPaid);
        loan.setBalanceOutstanding(newBalance.max(BigDecimal.ZERO));
        loan.setLastPaymentDate(payment.getPaymentDate());

        // Track processing fee payment for ON_REPAYMENT type
        BigDecimal processingFeePaidThisTime = BigDecimal.ZERO;
        if (loan.getProcessingFeeDeductionType() == LoanProduct.ProcessingFeeDeduction.ON_REPAYMENT 
                && loan.getProcessingFee() != null 
                && loan.getProcessingFee().compareTo(BigDecimal.ZERO) > 0
                && loan.getProcessingFeePaid().compareTo(loan.getProcessingFee()) < 0) {
            
            BigDecimal feeRemaining = loan.getProcessingFee().subtract(loan.getProcessingFeePaid());
            BigDecimal totalRemaining = loan.getTotalAmount().subtract(loan.getTotalPaid().subtract(amount));
            
            if (totalRemaining.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal feeRatio = loan.getProcessingFee().divide(loan.getTotalAmount(), 6, RoundingMode.HALF_UP);
                processingFeePaidThisTime = amount.multiply(feeRatio).setScale(2, RoundingMode.HALF_UP);
                processingFeePaidThisTime = processingFeePaidThisTime.min(feeRemaining);
            }
            
            loan.setProcessingFeePaid(loan.getProcessingFeePaid().add(processingFeePaidThisTime));
        }

        // Check if loan is cleared
        if (newBalance.compareTo(BigDecimal.ZERO) <= 0) {
            loan.setStatus(Loan.LoanStatus.CLEARED);
            loan.setBalanceOutstanding(BigDecimal.ZERO);
            loan.setClearedDate(LocalDate.now());
            if (loan.getProcessingFee() != null) {
                loan.setProcessingFeePaid(loan.getProcessingFee());
            }
            log.info("Loan {} fully cleared", loan.getId());
        } else {
            loan.setNextDueDate(LocalDate.now().plusMonths(1));
            if (loan.getStatus() == Loan.LoanStatus.OVERDUE) {
                loan.setStatus(Loan.LoanStatus.ACTIVE);
            }
        }
        
        loanRepository.save(loan);

        // Update member outstanding loan total
        Member member = loan.getMember();
        BigDecimal memberOutstanding = loanRepository.sumOutstandingByMemberId(member.getId());
        member.setOutstandingLoan(memberOutstanding != null ? memberOutstanding : BigDecimal.ZERO);
        memberRepository.save(member);

        // Create transactions - split into principal and interest
        BigDecimal interestPortion = BigDecimal.ZERO;
        BigDecimal principalPortion = amount;
        
        if (loan.getTotalInterest() != null && loan.getTotalInterest().compareTo(BigDecimal.ZERO) > 0 
                && loan.getTotalAmount().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal interestRatio = loan.getTotalInterest().divide(loan.getTotalAmount(), 6, RoundingMode.HALF_UP);
            interestPortion = amount.multiply(interestRatio).setScale(2, RoundingMode.HALF_UP);
            principalPortion = amount.subtract(interestPortion);
        }
        
        // Principal repayment transaction
        Transaction transaction = Transaction.builder()
                .date(payment.getPaymentDate())
                .type(Transaction.TransactionType.LOAN_REPAYMENT)
                .description("Loan principal repayment from " + member.getName())
                .amount(principalPortion)
                .direction(Transaction.TransactionDirection.IN)
                .member(member)
                .referenceId(loan.getId())
                .referenceType("LOAN")
                .tenant(loan.getTenant())
                .build();
        transactionRepository.save(transaction);

        // Interest income transaction
        if (interestPortion.compareTo(BigDecimal.ZERO) > 0) {
            Transaction interestTx = Transaction.builder()
                    .date(payment.getPaymentDate())
                    .type(Transaction.TransactionType.LOAN_INTEREST)
                    .description("Loan interest income from " + member.getName())
                    .amount(interestPortion)
                    .direction(Transaction.TransactionDirection.IN)
                    .member(member)
                    .referenceId(loan.getId())
                    .referenceType("LOAN")
                    .tenant(loan.getTenant())
                    .build();
            transactionRepository.save(interestTx);
        }

        // Processing fee transaction
        if (processingFeePaidThisTime.compareTo(BigDecimal.ZERO) > 0) {
            Transaction feeTx = Transaction.builder()
                    .date(payment.getPaymentDate())
                    .type(Transaction.TransactionType.PROCESSING_FEE)
                    .description("Loan processing fee (repayment) from " + member.getName())
                    .amount(processingFeePaidThisTime)
                    .direction(Transaction.TransactionDirection.IN)
                    .member(member)
                    .referenceId(loan.getId())
                    .referenceType("LOAN")
                    .tenant(loan.getTenant())
                    .build();
            transactionRepository.save(feeTx);
        }

        log.info("Payment {} verified by {} - loan balance updated to {}", paymentId, verifiedBy.getName(), loan.getBalanceOutstanding());
        return toPaymentDto(payment);
    }

    /**
     * Reject a pending payment (admin action).
     */
    @Transactional
    public LoanPaymentDto rejectPayment(Long paymentId, String rejectionReason, Long tenantId, Long rejectedByMemberId) {
        LoanPayment payment = loanPaymentRepository.findByIdAndTenantId(paymentId, tenantId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        if (payment.getStatus() != LoanPayment.PaymentStatus.PENDING) {
            throw new RuntimeException("Payment is not pending verification");
        }

        Member rejectedBy = memberRepository.findById(rejectedByMemberId)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        payment.setStatus(LoanPayment.PaymentStatus.REJECTED);
        payment.setVerifiedBy(rejectedBy); // Store who rejected it
        payment.setRejectionReason(rejectionReason);
        payment.setRejectedAt(LocalDateTime.now());
        
        payment = loanPaymentRepository.save(payment);
        log.info("Payment {} rejected by {}: {}", paymentId, rejectedBy.getName(), rejectionReason);

        return toPaymentDto(payment);
    }

    /**
     * Get all pending payments for verification (admin view).
     */
    public List<LoanPaymentDto> getPendingPayments(Long tenantId) {
        return loanPaymentRepository.findByTenantIdAndStatusOrderByRecordedAtDesc(tenantId, LoanPayment.PaymentStatus.PENDING)
                .stream()
                .map(this::toPaymentDto)
                .collect(Collectors.toList());
    }

    /**
     * Get payment history for a specific loan.
     */
    public List<LoanPaymentDto> getPaymentsForLoan(Long loanId, Long tenantId) {
        // Verify loan belongs to tenant
        loanRepository.findByIdAndTenantId(loanId, tenantId)
                .orElseThrow(() -> new RuntimeException("Loan not found"));
        
        return loanPaymentRepository.findByLoanIdOrderByPaymentDateDesc(loanId)
                .stream()
                .map(this::toPaymentDto)
                .collect(Collectors.toList());
    }

    /**
     * Get payments for a member (their own payments).
     */
    public List<LoanPaymentDto> getMyPayments(Long memberId) {
        return loanPaymentRepository.findByMemberIdOrderByPaymentDateDesc(memberId)
                .stream()
                .map(this::toPaymentDto)
                .collect(Collectors.toList());
    }

    /**
     * Count pending payments (for notification badge).
     */
    public long countPendingPayments(Long tenantId) {
        return loanPaymentRepository.countPendingByTenantId(tenantId);
    }

    private LoanPaymentDto toPaymentDto(LoanPayment payment) {
        Loan loan = payment.getLoan();
        return LoanPaymentDto.builder()
                .id(payment.getId())
                .loanId(loan.getId())
                .memberId(payment.getMember().getId())
                .memberName(payment.getMember().getName())
                .memberNo(payment.getMember().getMemberNo())
                .loanProductName(loan.getLoanProduct() != null ? loan.getLoanProduct().getName() : "Custom")
                .loanPrincipal(loan.getPrincipal())
                .loanBalance(loan.getBalanceOutstanding())
                .amount(payment.getAmount())
                .paymentDate(payment.getPaymentDate().toString())
                .paymentMethod(payment.getPaymentMethod())
                .transactionReference(payment.getTransactionReference())
                .notes(payment.getNotes())
                .status(payment.getStatus().name())
                .recordedById(payment.getRecordedBy() != null ? payment.getRecordedBy().getId() : null)
                .recordedByName(payment.getRecordedBy() != null ? payment.getRecordedBy().getName() : null)
                .recordedAt(payment.getRecordedAt() != null ? payment.getRecordedAt().format(DATETIME_FORMATTER) : null)
                .verifiedById(payment.getVerifiedBy() != null ? payment.getVerifiedBy().getId() : null)
                .verifiedByName(payment.getVerifiedBy() != null ? payment.getVerifiedBy().getName() : null)
                .verifiedAt(payment.getVerifiedAt() != null ? payment.getVerifiedAt().format(DATETIME_FORMATTER) : null)
                .rejectionReason(payment.getRejectionReason())
                .rejectedAt(payment.getRejectedAt() != null ? payment.getRejectedAt().format(DATETIME_FORMATTER) : null)
                .build();
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
        // Get all approvals ordered by type (guarantors first) then level
        List<LoanApproval> approvals = loanApprovalRepository.findByLoanIdOrderByTypeAndLevel(loan.getId());
        
        List<LoanDto.LoanApprovalDto> approvalDtos = approvals.stream()
                .map(a -> LoanDto.LoanApprovalDto.builder()
                        .approvalType(a.getApprovalType().name())
                        .level(a.getLevel())
                        .levelName(a.getApprovalType() == LoanApproval.ApprovalType.GUARANTOR 
                                ? "Guarantor" 
                                : getLevelName(loan, a.getLevel()))
                        .approverName(a.getApprover().getName())
                        .approverRole(a.getApproverRole() != null ? a.getApproverRole().getName() : null)
                        .action(a.getAction().name())
                        .comments(a.getComments())
                        .actionDate(a.getActionDate() != null ? a.getActionDate().format(DATETIME_FORMATTER) : null)
                        .build())
                .collect(Collectors.toList());

        // Determine pending approval info
        String pendingApprovalRole = null;
        String pendingApprovalType = null;
        List<String> pendingGuarantorNames = new ArrayList<>();
        
        if (loan.getStatus() == Loan.LoanStatus.PENDING_GUARANTORS) {
            pendingApprovalType = "GUARANTOR";
            // Get names of guarantors who haven't approved yet
            List<Long> approvedGuarantorIds = loanApprovalRepository.findGuarantorApprovalsByLoanId(loan.getId())
                    .stream()
                    .map(a -> a.getApprover().getId())
                    .collect(Collectors.toList());
            
            pendingGuarantorNames = loan.getGuarantors().stream()
                    .filter(g -> !approvedGuarantorIds.contains(g.getId()))
                    .map(Member::getName)
                    .collect(Collectors.toList());
            
            pendingApprovalRole = pendingGuarantorNames.size() + " Guarantor(s)";
        } else if (loan.getStatus() == Loan.LoanStatus.PENDING || loan.getStatus() == Loan.LoanStatus.PENDING_APPROVAL) {
            pendingApprovalType = "APPROVER";
            if (loan.getLoanProduct() != null) {
                int nextLevel = loan.getCurrentApprovalLevel() + 1;
                pendingApprovalRole = approvalLevelRepository.findByLoanProductIdAndLevel(loan.getLoanProduct().getId(), nextLevel)
                        .map(level -> {
                            if (level.getMember() != null) {
                                return level.getMember().getName();
                            } else if (level.getRole() != null) {
                                return level.getRole().getName();
                            }
                            return "Level " + nextLevel;
                        })
                        .orElse("Level " + nextLevel);
            }
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
                .processingFee(loan.getProcessingFee())
                .processingFeePaid(loan.getProcessingFeePaid())
                .netDisbursementAmount(loan.getNetDisbursementAmount())
                .processingFeeDeductionType(loan.getProcessingFeeDeductionType() != null 
                        ? loan.getProcessingFeeDeductionType().name() : null)
                .status(loan.getStatus().name())
                .requiredGuarantors(loan.getRequiredGuarantors())
                .guarantorsApprovedCount(loan.getGuarantorsApprovedCount())
                .pendingGuarantorNames(pendingGuarantorNames)
                .currentApprovalLevel(loan.getCurrentApprovalLevel())
                .requiredApprovalLevels(loan.getRequiredApprovalLevels())
                .pendingApprovalRole(pendingApprovalRole)
                .pendingApprovalType(pendingApprovalType)
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
    
    // ==================== LOAN INCOME ====================
    
    public LoanIncomeSummaryDto getLoanIncomeSummary(Long tenantId, LocalDate startDate, LocalDate endDate) {
        BigDecimal totalInterest = transactionRepository.sumLoanInterestByTenantId(tenantId);
        BigDecimal totalFees = transactionRepository.sumProcessingFeesByTenantId(tenantId);
        
        BigDecimal periodInterest = BigDecimal.ZERO;
        BigDecimal periodFees = BigDecimal.ZERO;
        
        if (startDate != null && endDate != null) {
            periodInterest = transactionRepository.sumLoanInterestByTenantIdAndDateBetween(tenantId, startDate, endDate);
            periodFees = transactionRepository.sumProcessingFeesByTenantIdAndDateBetween(tenantId, startDate, endDate);
        } else {
            periodInterest = totalInterest;
            periodFees = totalFees;
        }
        
        return LoanIncomeSummaryDto.builder()
                .totalInterestCollected(totalInterest != null ? totalInterest : BigDecimal.ZERO)
                .totalProcessingFeesCollected(totalFees != null ? totalFees : BigDecimal.ZERO)
                .totalLoanIncome(totalInterest.add(totalFees))
                .periodInterest(periodInterest != null ? periodInterest : BigDecimal.ZERO)
                .periodFees(periodFees != null ? periodFees : BigDecimal.ZERO)
                .periodTotal(periodInterest.add(periodFees))
                .build();
    }
    
    public Page<Transaction> getLoanIncomeTransactions(Long tenantId, Pageable pageable) {
        return transactionRepository.findLoanIncomeByTenantId(tenantId, pageable);
    }
}
