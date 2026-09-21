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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private final PayableRepository payableRepository;
    private final PaymentClaimRepository paymentClaimRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int DEFAULT_PAGE_SIZE = 10;

    // ==================== GET METHODS ====================

    public List<LoanDto> getAllLoans(Long tenantId) {
        List<Loan> loans = loanRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        return toDtoListWithPendingClaims(loans, tenantId);
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
        
        // Batch fetch pending claims for this page (with payableIds)
        List<Loan> loans = loanPage.getContent();
        List<Long> activeIds = loans.stream()
                .filter(l -> l.getStatus() == Loan.LoanStatus.ACTIVE || l.getStatus() == Loan.LoanStatus.OVERDUE)
                .map(Loan::getId)
                .collect(Collectors.toList());
        
        // Map loanId -> payableId for pending claims
        Map<Long, Long> pendingClaimPayableIds = new java.util.HashMap<>();
        Map<Long, BigDecimal> pendingPaymentAmounts = new java.util.HashMap<>();
        if (!activeIds.isEmpty()) {
            List<Payable> pendingPayables = payableRepository.findByTenant_IdAndPayableTypeAndPayableRefIdInAndStatus(
                    tenantId, PayableType.LOAN_REPAYMENT, activeIds, PayableStatus.AWAITING_CONFIRMATION);
            for (Payable payable : pendingPayables) {
                pendingClaimPayableIds.put(payable.getPayableRefId(), payable.getId());
                pendingPaymentAmounts.put(payable.getPayableRefId(), payable.getAmount());
            }
        }
        
        Page<LoanDto> dtoPage = loanPage.map(l -> {
            boolean hasPendingClaim = pendingClaimPayableIds.containsKey(l.getId());
            Long payableId = pendingClaimPayableIds.get(l.getId());
            BigDecimal pendingAmount = pendingPaymentAmounts.get(l.getId());
            return toDto(l, hasPendingClaim, payableId, pendingAmount);
        });
        return PageResponse.from(dtoPage);
    }
    
    /**
     * Get my loans with pagination.
     */
    public PageResponse<LoanDto> getMyLoansPaginated(Long tenantId, Long memberId, int page, int size) {
        if (size <= 0) size = DEFAULT_PAGE_SIZE;
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        
        Page<Loan> loanPage = loanRepository.findByMemberIdOrderByCreatedAtDesc(memberId, pageable);
        
        // Batch fetch pending claims for this page (with payableIds)
        List<Loan> loans = loanPage.getContent();
        List<Long> activeIds = loans.stream()
                .filter(l -> l.getStatus() == Loan.LoanStatus.ACTIVE || l.getStatus() == Loan.LoanStatus.OVERDUE)
                .map(Loan::getId)
                .collect(Collectors.toList());
        
        // Map loanId -> payableId for pending claims
        Map<Long, Long> pendingClaimPayableIds = new java.util.HashMap<>();
        Map<Long, BigDecimal> pendingPaymentAmounts = new java.util.HashMap<>();
        if (!activeIds.isEmpty()) {
            List<Payable> pendingPayables = payableRepository.findByTenant_IdAndPayableTypeAndPayableRefIdInAndStatus(
                    tenantId, PayableType.LOAN_REPAYMENT, activeIds, PayableStatus.AWAITING_CONFIRMATION);
            for (Payable payable : pendingPayables) {
                pendingClaimPayableIds.put(payable.getPayableRefId(), payable.getId());
                pendingPaymentAmounts.put(payable.getPayableRefId(), payable.getAmount());
            }
        }
        
        Page<LoanDto> dtoPage = loanPage.map(l -> {
            boolean hasPendingClaim = pendingClaimPayableIds.containsKey(l.getId());
            Long payableId = pendingClaimPayableIds.get(l.getId());
            BigDecimal pendingAmount = pendingPaymentAmounts.get(l.getId());
            return toDto(l, hasPendingClaim, payableId, pendingAmount);
        });
        return PageResponse.from(dtoPage);
    }

    public List<LoanDto> getMyLoans(Long tenantId, Long memberId) {
        List<Loan> loans = loanRepository.findByMemberIdOrderByCreatedAtDesc(memberId).stream()
                .filter(l -> l.getTenant().getId().equals(tenantId))
                .collect(Collectors.toList());
        return toDtoListWithPendingClaims(loans, tenantId);
    }

    public List<LoanDto> getPendingApprovals(Long tenantId, Long memberId) {
        log.info("=== getPendingApprovals DEBUG ===");
        log.info("tenantId={}, memberId={}", tenantId, memberId);
        
        // Get all loans pending approval (includes PENDING_GUARANTORS, PENDING, PENDING_APPROVAL)
        List<Loan> pendingLoans = loanRepository.findAwaitingApprovalByTenantId(tenantId);
        log.info("Found {} pending loans", pendingLoans.size());
        
        // Get the member to check their role
        Member currentMember = memberRepository.findById(memberId).orElse(null);
        boolean isAdmin = currentMember != null && currentMember.getRole() != null && 
                currentMember.getRole().getPermissions().stream()
                        .anyMatch(p -> "LOANS".equalsIgnoreCase(p.getModule()) && p.isCanApprove());
        log.info("currentMember={}, isAdmin={}", currentMember != null ? currentMember.getName() : "null", isAdmin);
        
        // Filter to only loans where this member is the CURRENT guarantor OR next approver (sequential)
        List<LoanDto> result = pendingLoans.stream()
                .filter(loan -> {
                    log.info("Checking loan {} (status={})", loan.getId(), loan.getStatus());
                    
                    // Check if member is the CURRENT guarantor for this loan (Phase 1 - sequential)
                    if (loan.getStatus() == Loan.LoanStatus.PENDING_GUARANTORS) {
                        Member currentGuarantor = loan.getCurrentGuarantor();
                        if (currentGuarantor != null) {
                            boolean isCurrentGuarantor = currentGuarantor.getId().equals(memberId);
                            log.info("  PENDING_GUARANTORS: currentGuarantor={}, isCurrentGuarantor={}", 
                                    currentGuarantor.getName(), isCurrentGuarantor);
                            if (isCurrentGuarantor) {
                                return true; // Show this loan - member is the current guarantor in sequence
                            }
                        }
                    }
                    
                    // Check if member is the next approver (Phase 2 - sequential approval)
                    if (loan.getStatus() == Loan.LoanStatus.PENDING || 
                        loan.getStatus() == Loan.LoanStatus.PENDING_APPROVAL) {
                        
                        // If no loan product, check if user has APPROVE_LOAN permission
                        if (loan.getLoanProduct() == null) {
                            log.info("  No loan product, using isAdmin check: {}", isAdmin);
                            return isAdmin;
                        }
                        
                        int nextLevel = loan.getCurrentApprovalLevel() + 1;
                        log.info("  PENDING/PENDING_APPROVAL: nextLevel={}, requiredLevels={}", nextLevel, loan.getRequiredApprovalLevels());
                        
                        // If no approval levels are configured (requiredApprovalLevels = 0), allow anyone with APPROVE_LOAN
                        if (loan.getRequiredApprovalLevels() == 0) {
                            log.info("  No approval levels configured, using isAdmin check: {}", isAdmin);
                            return isAdmin;
                        }
                        
                        return approvalLevelRepository.findByLoanProductIdAndLevel(loan.getLoanProduct().getId(), nextLevel)
                                .map(level -> {
                                    log.info("  Found approval level: member={}, role={}", 
                                            level.getMember() != null ? level.getMember().getName() : "null",
                                            level.getRole() != null ? level.getRole().getName() : "null");
                                    // Check if this specific member is the approver
                                    if (level.getMember() != null) {
                                        boolean match = level.getMember().getId().equals(memberId);
                                        log.info("  Member check: {}", match);
                                        return match;
                                    }
                                    // Fallback to role-based check
                                    if (currentMember != null && currentMember.getRole() != null && level.getRole() != null) {
                                        boolean match = level.getRole().getId().equals(currentMember.getRole().getId());
                                        log.info("  Role check: {}", match);
                                        return match;
                                    }
                                    return false;
                                })
                                .orElseGet(() -> {
                                    // No approval level found for this level - allow admin
                                    log.info("  No approval level found for level {}, using isAdmin: {}", nextLevel, isAdmin);
                                    return isAdmin;
                                });
                    }
                    
                    return false;
                })
                .map(this::toDto)
                .collect(Collectors.toList());
        
        log.info("Returning {} pending approvals", result.size());
        return result;
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

        // Get the current guarantor who should approve (sequential order)
        Member currentGuarantor = loan.getCurrentGuarantor();
        if (currentGuarantor == null) {
            throw new RuntimeException("No more guarantors pending");
        }
        
        // Verify this member is the CURRENT guarantor in sequence
        if (!currentGuarantor.getId().equals(guarantorId)) {
            throw new RuntimeException("It's not your turn to approve. Waiting for " + currentGuarantor.getName() + " to approve first.");
        }

        // Check if already approved (safety check)
        if (loanApprovalRepository.existsGuarantorApproval(loanId, guarantorId)) {
            throw new RuntimeException("You have already approved this loan as guarantor");
        }

        // Record the guarantor approval with level = currentGuarantorIndex + 1 (1-based)
        int currentIndex = loan.getCurrentGuarantorIndex() != null ? loan.getCurrentGuarantorIndex() : 0;
        int guarantorLevel = currentIndex + 1;
        LoanApproval approval = LoanApproval.builder()
                .loan(loan)
                .approvalType(LoanApproval.ApprovalType.GUARANTOR)
                .level(guarantorLevel)
                .approver(guarantor)
                .approverRole(guarantor.getRole())
                .action(LoanApproval.ApprovalAction.APPROVED)
                .comments(comments)
                .actionDate(LocalDateTime.now())
                .build();
        loanApprovalRepository.save(approval);

        // Update guarantor count and move to next guarantor
        int approvedCount = loan.getGuarantorsApprovedCount() != null ? loan.getGuarantorsApprovedCount() : 0;
        loan.setGuarantorsApprovedCount(approvedCount + 1);
        loan.setCurrentGuarantorIndex(currentIndex + 1);

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
            Member nextGuarantor = loan.getGuarantors().get(loan.getCurrentGuarantorIndex());
            log.info("Loan {} - guarantor {} approved ({}/{}), next: {}", loanId, guarantor.getName(), 
                    loan.getGuarantorsApprovedCount(), loan.getRequiredGuarantors(), nextGuarantor.getName());
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
            // Verify this member is a guarantor and get their position
            int guarantorIndex = -1;
            List<Member> guarantors = loan.getGuarantors();
            for (int i = 0; i < guarantors.size(); i++) {
                if (guarantors.get(i).getId().equals(rejecterId)) {
                    guarantorIndex = i;
                    break;
                }
            }
            if (guarantorIndex < 0) {
                throw new RuntimeException("You are not a guarantor for this loan");
            }
            approvalType = LoanApproval.ApprovalType.GUARANTOR;
            level = guarantorIndex + 1; // 1-based index for guarantors
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
            loan.setNextDueDate(null);
            
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
            loan.setNextDueDate(null);
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
        // For single loan lookup, do individual query for pending claim
        boolean hasPendingClaim = false;
        Long payableId = null;
        BigDecimal pendingAmount = null;
        if (loan.getStatus() == Loan.LoanStatus.ACTIVE || loan.getStatus() == Loan.LoanStatus.OVERDUE) {
            Optional<Payable> payableOpt = payableRepository.findByPayableTypeAndPayableRefId(PayableType.LOAN_REPAYMENT, loan.getId());
            if (payableOpt.isPresent() && payableOpt.get().getStatus() == PayableStatus.AWAITING_CONFIRMATION) {
                hasPendingClaim = true;
                payableId = payableOpt.get().getId();
                pendingAmount = payableOpt.get().getAmount();
            }
        }
        return toDto(loan, hasPendingClaim, payableId, pendingAmount);
    }
    
    private LoanDto toDto(Loan loan, boolean hasPendingClaim) {
        return toDto(loan, hasPendingClaim, null, null);
    }
    
    private LoanDto toDto(Loan loan, boolean hasPendingClaim, Long payableId, BigDecimal pendingPaymentAmount) {
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
        String approvalProgress = null;
        List<String> pendingGuarantorNames = new ArrayList<>();
        
        if (loan.getStatus() == Loan.LoanStatus.PENDING_GUARANTORS) {
            pendingApprovalType = "GUARANTOR";
            
            // Get the CURRENT guarantor who needs to approve (sequential flow)
            Member currentGuarantor = loan.getCurrentGuarantor();
            if (currentGuarantor != null) {
                pendingApprovalRole = currentGuarantor.getName();
            }
            
            // Get ALL remaining guarantors (current and future) for display
            List<Member> allGuarantors = loan.getGuarantors();
            int currentIndex = loan.getCurrentGuarantorIndex() != null ? loan.getCurrentGuarantorIndex() : 0;
            if (allGuarantors != null && currentIndex < allGuarantors.size()) {
                // Add all guarantors from current index onwards (those who haven't approved yet)
                for (int i = currentIndex; i < allGuarantors.size(); i++) {
                    Member guarantor = allGuarantors.get(i);
                    if (guarantor != null) {
                        pendingGuarantorNames.add(guarantor.getName());
                    }
                }
            }
            
            // Build approval progress message
            int approved = loan.getGuarantorsApprovedCount() != null ? loan.getGuarantorsApprovedCount() : 0;
            int required = loan.getRequiredGuarantors() != null ? loan.getRequiredGuarantors() : 0;
            if (currentGuarantor != null) {
                approvalProgress = String.format("Waiting for %s (%d/%d guarantors approved)", 
                        currentGuarantor.getName(), approved, required);
            } else {
                approvalProgress = String.format("%d/%d guarantors approved", approved, required);
            }
            
            // Also show remaining guarantors info for display
            int remaining = required - approved;
            if (remaining > 1) {
                pendingApprovalRole = currentGuarantor != null 
                    ? currentGuarantor.getName() + " (+" + (remaining - 1) + " more after)"
                    : remaining + " Guarantor(s) remaining";
            }
        } else if (loan.getStatus() == Loan.LoanStatus.PENDING || loan.getStatus() == Loan.LoanStatus.PENDING_APPROVAL) {
            pendingApprovalType = "APPROVER";
            int nextLevel = loan.getCurrentApprovalLevel() + 1;
            int requiredLevels = loan.getRequiredApprovalLevels();
            
            if (loan.getLoanProduct() != null && requiredLevels > 0) {
                String nextApproverName = approvalLevelRepository.findByLoanProductIdAndLevel(loan.getLoanProduct().getId(), nextLevel)
                        .map(level -> {
                            if (level.getMember() != null) {
                                return level.getMember().getName();
                            } else if (level.getRole() != null) {
                                return level.getRole().getName();
                            }
                            return "Level " + nextLevel;
                        })
                        .orElse("Level " + nextLevel);
                
                pendingApprovalRole = nextApproverName;
                
                // Build approval progress
                int guarantorsApproved = loan.getGuarantorsApprovedCount() != null ? loan.getGuarantorsApprovedCount() : 0;
                int guarantorsRequired = loan.getRequiredGuarantors() != null ? loan.getRequiredGuarantors() : 0;
                int approversApproved = loan.getCurrentApprovalLevel() != null ? loan.getCurrentApprovalLevel() : 0;
                
                if (guarantorsRequired > 0) {
                    approvalProgress = String.format("%d/%d guarantors ✓, waiting for %s (%d/%d approvers)", 
                            guarantorsApproved, guarantorsRequired, nextApproverName, approversApproved, requiredLevels);
                } else {
                    approvalProgress = String.format("Waiting for %s (%d/%d approvers)", 
                            nextApproverName, approversApproved, requiredLevels);
                }
                
                // Show how many levels remain
                int remaining = requiredLevels - approversApproved;
                if (remaining > 1) {
                    pendingApprovalRole = pendingApprovalRole + " (+" + (remaining - 1) + " more after)";
                }
            } else {
                pendingApprovalRole = "Admin Approval";
                approvalProgress = "Waiting for admin approval";
            }
        } else if (loan.getStatus() == Loan.LoanStatus.APPROVED) {
            approvalProgress = "Fully approved, awaiting disbursement";
        } else if (loan.getStatus() == Loan.LoanStatus.ACTIVE) {
            approvalProgress = "Loan active";
        } else if (loan.getStatus() == Loan.LoanStatus.CLEARED) {
            approvalProgress = "Loan cleared";
        } else if (loan.getStatus() == Loan.LoanStatus.REJECTED) {
            approvalProgress = "Loan rejected";
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
                .hasPendingClaim(hasPendingClaim)
                .payableId(payableId)
                .pendingPaymentAmount(pendingPaymentAmount)
                .requiredGuarantors(loan.getRequiredGuarantors())
                .guarantorsApprovedCount(loan.getGuarantorsApprovedCount())
                .currentGuarantorIndex(loan.getCurrentGuarantorIndex())
                .pendingGuarantorNames(pendingGuarantorNames)
                .approvalProgress(approvalProgress)
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
    
    /**
     * Convert list of loans to DTOs with batch pending claim lookup.
     */
    private List<LoanDto> toDtoListWithPendingClaims(List<Loan> loans, Long tenantId) {
        if (loans.isEmpty()) return List.of();
        
        // Get IDs of active/overdue loans
        List<Long> activeIds = loans.stream()
                .filter(l -> l.getStatus() == Loan.LoanStatus.ACTIVE || l.getStatus() == Loan.LoanStatus.OVERDUE)
                .map(Loan::getId)
                .collect(Collectors.toList());
        
        // Batch fetch payables with pending claims (maps loanId -> payableId and loanId -> amount)
        Map<Long, Long> pendingClaimPayableIds = new java.util.HashMap<>();
        Map<Long, BigDecimal> pendingPaymentAmounts = new java.util.HashMap<>();
        if (!activeIds.isEmpty()) {
            List<Payable> pendingPayables = payableRepository.findByTenant_IdAndPayableTypeAndPayableRefIdInAndStatus(
                    tenantId, PayableType.LOAN_REPAYMENT, activeIds, PayableStatus.AWAITING_CONFIRMATION);
            for (Payable payable : pendingPayables) {
                pendingClaimPayableIds.put(payable.getPayableRefId(), payable.getId());
                pendingPaymentAmounts.put(payable.getPayableRefId(), payable.getAmount());
            }
        }
        
        return loans.stream()
                .map(l -> {
                    boolean hasPendingClaim = pendingClaimPayableIds.containsKey(l.getId());
                    Long payableId = pendingClaimPayableIds.get(l.getId());
                    BigDecimal pendingAmount = pendingPaymentAmounts.get(l.getId());
                    return toDto(l, hasPendingClaim, payableId, pendingAmount);
                })
                .collect(Collectors.toList());
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
