package com.calpex_technologies.chama.service;

import com.calpex_technologies.chama.dto.*;
import com.calpex_technologies.chama.entity.*;
import com.calpex_technologies.chama.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final TenantRepository tenantRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final ContributionRepository contributionRepository;
    private final LoanRepository loanRepository;
    private final FineRepository fineRepository;
    private final PenaltyRepository penaltyRepository;
    private final TransactionRepository transactionRepository;
    private final PayoutMemberRepository payoutMemberRepository;
    private final WelfareContributionRepository welfareContributionRepository;
    private final WelfareEventRepository welfareEventRepository;
    private final MerryGoRoundContributionRepository mgrContributionRepository;
    private final MerryGoRoundRoundRepository mgrRoundRepository;
    private final ShareCalculationService shareCalculationService;
    private final EmailService emailService;
    
    private static final int DEFAULT_PAGE_SIZE = 10;

    @Transactional(readOnly = true)
    public List<MemberDto> getAllMembers(Long tenantId) {
        log.info("MemberService.getAllMembers called with tenantId={}", tenantId);
        List<Member> members = memberRepository.findByTenantIdWithRole(tenantId);
        log.info("Found {} members for tenantId={}", members.size(), tenantId);
        
        List<MemberDto> memberDtos = members.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        
        // Fetch share percentages from ShareCalculationService and enrich member DTOs
        ShareCalculationDto shareCalc = shareCalculationService.calculateShares(tenantId);
        Map<Long, BigDecimal> memberSharePercentages = new HashMap<>();
        if (shareCalc.getMemberShares() != null) {
            for (MemberShareDto share : shareCalc.getMemberShares()) {
                memberSharePercentages.put(share.getMemberId(), share.getSharePercentage());
            }
        }
        
        // Update totalShares with share percentage from calculation
        for (MemberDto dto : memberDtos) {
            BigDecimal sharePercentage = memberSharePercentages.getOrDefault(dto.getId(), BigDecimal.ZERO);
            dto.setTotalShares(sharePercentage);
        }
        
        return memberDtos;
    }
    
    /**
     * Get members with pagination, search, and status filter.
     */
    @Transactional(readOnly = true)
    public PageResponse<MemberDto> getMembersPaginated(Long tenantId, int page, int size, 
                                                        String search, String status) {
        if (size <= 0) size = DEFAULT_PAGE_SIZE;
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        
        Page<Member> memberPage;
        
        boolean hasSearch = search != null && !search.trim().isEmpty();
        boolean hasStatus = status != null && !status.equalsIgnoreCase("ALL") && !status.trim().isEmpty();
        
        if (hasSearch && hasStatus) {
            Member.MemberStatus memberStatus = Member.MemberStatus.valueOf(status.toUpperCase());
            memberPage = memberRepository.findByTenantIdWithRoleStatusAndSearch(tenantId, memberStatus, search.trim(), pageable);
        } else if (hasSearch) {
            memberPage = memberRepository.findByTenantIdWithRoleAndSearch(tenantId, search.trim(), pageable);
        } else if (hasStatus) {
            Member.MemberStatus memberStatus = Member.MemberStatus.valueOf(status.toUpperCase());
            memberPage = memberRepository.findByTenantIdWithRoleAndStatus(tenantId, memberStatus, pageable);
        } else {
            memberPage = memberRepository.findByTenantIdWithRole(tenantId, pageable);
        }
        
        // Convert to DTOs, explicitly fetching role name within transaction
        Page<MemberDto> dtoPage = memberPage.map(member -> {
            String roleName = "Member";
            Long roleId = null;
            if (member.getRole() != null) {
                // Force load within transaction
                Role role = member.getRole();
                roleName = role.getName();
                roleId = role.getId();
            }
            return MemberDto.builder()
                    .id(member.getId())
                    .name(member.getName())
                    .phone(member.getPhone())
                    .email(member.getEmail())
                    .memberNo(member.getMemberNo())
                    .joinDate(member.getJoinDate() != null ? member.getJoinDate().toString() : null)
                    .status(member.getStatus().name())
                    .role(roleName)
                    .roleId(roleId)
                    .totalSavings(member.getTotalSavings())
                    .totalShares(member.getTotalShares())
                    .outstandingLoan(member.getOutstandingLoan())
                    .arrearsMonths(member.getArrearsMonths())
                    .build();
        });
        
        // Fetch share percentages from ShareCalculationService and enrich member DTOs
        ShareCalculationDto shareCalc = shareCalculationService.calculateShares(tenantId);
        Map<Long, BigDecimal> memberSharePercentages = new HashMap<>();
        if (shareCalc.getMemberShares() != null) {
            for (MemberShareDto share : shareCalc.getMemberShares()) {
                memberSharePercentages.put(share.getMemberId(), share.getSharePercentage());
            }
        }
        
        // Update totalShares with share percentage from calculation
        List<MemberDto> enrichedMembers = dtoPage.getContent().stream()
                .map(dto -> {
                    BigDecimal sharePercentage = memberSharePercentages.getOrDefault(dto.getId(), BigDecimal.ZERO);
                    dto.setTotalShares(sharePercentage);
                    return dto;
                })
                .collect(Collectors.toList());
        
        return PageResponse.from(dtoPage);
    }

    @Transactional(readOnly = true)
    public MemberDto getMember(Long id, Long tenantId) {
        Member member = memberRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Member not found"));
        return toDto(member);
    }

    @Transactional
    public MemberDto createMember(CreateMemberRequest request, Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        if (memberRepository.existsByEmailAndTenant(request.getEmail(), tenant)) {
            throw new RuntimeException("Email already exists");
        }

        long count = memberRepository.countByTenantId(tenantId);
        String memberNo = String.format("M%03d", count + 1);

        Role role = null;
        if (request.getRoleId() != null) {
            role = roleRepository.findByIdAndTenantId(request.getRoleId(), tenantId).orElse(null);
        }
        if (role == null) {
            role = roleRepository.findByNameAndTenantId("Member", tenantId).orElse(null);
        }

        // Use provided password or default
        String plainPassword = request.getPassword() != null && !request.getPassword().isEmpty() 
                ? request.getPassword() 
                : "12345678";

        Member member = Member.builder()
                .name(request.getName())
                .phone(request.getPhone())
                .email(request.getEmail())
                .password(passwordEncoder.encode(plainPassword))
                .memberNo(memberNo)
                .joinDate(LocalDate.now())
                .status(Member.MemberStatus.ACTIVE)
                .role(role)
                .tenant(tenant)
                .firstLogin(true)  // Force password change on first login
                .mustChangePassword(false)  // Will be set to true on first login
                .build();

        member = memberRepository.save(member);
        
        // Send welcome email with login credentials
        if (request.getEmail() != null && !request.getEmail().isEmpty()) {
            emailService.sendWelcomeEmail(
                    request.getEmail(),
                    request.getName(),
                    plainPassword,
                    tenant.getName()
            );
        }

        return toDto(member);
    }

    @Transactional
    public MemberDto updateMember(Long id, MemberDto dto, Long tenantId) {
        Member member = memberRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        member.setName(dto.getName());
        member.setPhone(dto.getPhone());
        member.setEmail(dto.getEmail());
        
        if (dto.getStatus() != null) {
            member.setStatus(Member.MemberStatus.valueOf(dto.getStatus()));
        }

        member = memberRepository.save(member);
        return toDto(member);
    }

    @Transactional
    public void deleteMember(Long id, Long tenantId) {
        Member member = memberRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Member not found"));
        member.setStatus(Member.MemberStatus.EXITED);
        memberRepository.save(member);
    }

    /**
     * Hard delete a member and ALL related data across all modules.
     * This permanently removes the member and all their:
     * - Contributions
     * - Loans and loan repayments
     * - Fines
     * - Penalties
     * - Transactions
     * - Welfare contributions
     * - Merry-go-round contributions
     * - Share entries
     * - Payout records
     * 
     * WARNING: This action cannot be undone!
     */
    @Transactional
    public void hardDeleteMember(Long id, Long tenantId) {
        Member member = memberRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        log.warn("HARD DELETE: Permanently deleting member {} (ID: {}) and all related data", member.getName(), id);

        // 1. Delete contributions
        contributionRepository.deleteByMemberId(id);
        log.info("Deleted contributions for member {}", id);

        // 2. Delete loans (and their repayments via cascade or manual)
        List<Loan> loans = loanRepository.findByMemberIdOrderByCreatedAtDesc(id);
        for (Loan loan : loans) {
            // Delete loan repayments first if not cascaded
            // loanRepaymentRepository.deleteByLoanId(loan.getId());
            loanRepository.delete(loan);
        }
        log.info("Deleted {} loans for member {}", loans.size(), id);

        // 3. Delete fines (member fines from meetings)
        fineRepository.deleteByMemberId(id);
        log.info("Deleted fines for member {}", id);

        // 4. Delete penalties
        penaltyRepository.deleteByMemberId(id);
        log.info("Deleted penalties for member {}", id);

        // 5. Delete transactions referencing this member
        transactionRepository.deleteByMemberId(id);
        log.info("Deleted transactions for member {}", id);

        // 6. Delete welfare contributions
        welfareContributionRepository.deleteByMember(id);
        log.info("Deleted welfare contributions for member {}", id);

        // 7. Delete merry-go-round contributions
        mgrContributionRepository.deleteByMemberId(id);
        log.info("Deleted MGR contributions for member {}", id);

        // 8. Delete payout member records
        payoutMemberRepository.deleteByMemberId(id);
        log.info("Deleted payout records for member {}", id);

        // 9. Finally, delete the member
        memberRepository.delete(member);
        log.warn("HARD DELETE COMPLETE: Member {} (ID: {}) permanently deleted", member.getName(), id);
    }

    /**
     * Get comprehensive financial summary for a member
     */
    @Transactional(readOnly = true)
    public MemberFinancialSummaryDto getMemberFinancialSummary(Long memberId, Long tenantId) {
        Member member = memberRepository.findByIdAndTenantId(memberId, tenantId)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        // Get share calculation for this member
        ShareCalculationDto shareCalc = shareCalculationService.calculateShares(tenantId);
        MemberShareDto memberShare = shareCalc.getMemberShares().stream()
                .filter(s -> s.getMemberId().equals(memberId))
                .findFirst()
                .orElse(null);

        // ==================== CONTRIBUTIONS ====================
        List<Contribution> contributions = contributionRepository.findByMemberIdOrderByMonthDesc(memberId);
        
        BigDecimal totalContributionsDue = contributions.stream()
                .map(c -> c.getAmountDue() != null ? c.getAmountDue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalContributionsPaid = contributions.stream()
                .map(c -> c.getAmountPaid() != null ? c.getAmountPaid() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        int paidCount = (int) contributions.stream()
                .filter(c -> c.getStatus() == Contribution.ContributionStatus.PAID)
                .count();
        
        int pendingCount = (int) contributions.stream()
                .filter(c -> c.getStatus() == Contribution.ContributionStatus.PENDING 
                        || c.getStatus() == Contribution.ContributionStatus.PARTIAL
                        || c.getStatus() == Contribution.ContributionStatus.OVERDUE)
                .count();

        // Contributions by type
        Map<Long, List<Contribution>> byType = contributions.stream()
                .filter(c -> c.getContributionType() != null)
                .collect(Collectors.groupingBy(c -> c.getContributionType().getId()));
        
        List<MemberFinancialSummaryDto.ContributionSummaryDto> contributionsByType = new ArrayList<>();
        for (Map.Entry<Long, List<Contribution>> entry : byType.entrySet()) {
            List<Contribution> typeContribs = entry.getValue();
            if (!typeContribs.isEmpty()) {
                ContributionType ct = typeContribs.get(0).getContributionType();
                BigDecimal typeDue = typeContribs.stream()
                        .map(c -> c.getAmountDue() != null ? c.getAmountDue() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal typePaid = typeContribs.stream()
                        .map(c -> c.getAmountPaid() != null ? c.getAmountPaid() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                
                contributionsByType.add(MemberFinancialSummaryDto.ContributionSummaryDto.builder()
                        .contributionTypeId(ct.getId())
                        .contributionTypeName(ct.getName())
                        .totalDue(typeDue)
                        .totalPaid(typePaid)
                        .balance(typeDue.subtract(typePaid))
                        .monthsCount(typeContribs.size())
                        .build());
            }
        }

        // ==================== LOANS ====================
        List<Loan> loans = loanRepository.findByMemberIdOrderByCreatedAtDesc(memberId);
        
        BigDecimal totalLoansBorrowed = loans.stream()
                .filter(l -> l.getStatus() != Loan.LoanStatus.PENDING && l.getStatus() != Loan.LoanStatus.REJECTED)
                .map(l -> l.getPrincipal() != null ? l.getPrincipal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalLoansRepaid = loans.stream()
                .map(l -> l.getTotalPaid() != null ? l.getTotalPaid() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal outstandingLoanBalance = loans.stream()
                .filter(l -> l.getStatus() == Loan.LoanStatus.ACTIVE || l.getStatus() == Loan.LoanStatus.OVERDUE)
                .map(l -> {
                    BigDecimal total = l.getTotalAmount() != null ? l.getTotalAmount() : BigDecimal.ZERO;
                    BigDecimal paid = l.getTotalPaid() != null ? l.getTotalPaid() : BigDecimal.ZERO;
                    return total.subtract(paid);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        int activeLoansCount = (int) loans.stream()
                .filter(l -> l.getStatus() == Loan.LoanStatus.ACTIVE || l.getStatus() == Loan.LoanStatus.OVERDUE)
                .count();
        
        int clearedLoansCount = (int) loans.stream()
                .filter(l -> l.getStatus() == Loan.LoanStatus.CLEARED)
                .count();

        // ==================== FINES ====================
        List<Fine> fines = fineRepository.findByMemberId(memberId);
        
        BigDecimal totalFinesImposed = fines.stream()
                .map(f -> f.getAmount() != null ? f.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalFinesPaid = fines.stream()
                .filter(f -> f.getStatus() == Fine.FineStatus.PAID)
                .map(f -> f.getPaidAmount() != null ? f.getPaidAmount() : f.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // ==================== PENALTIES ====================
        BigDecimal totalPenaltiesPaid = penaltyRepository.getTotalPaidPenaltiesForMember(memberId);
        if (totalPenaltiesPaid == null) totalPenaltiesPaid = BigDecimal.ZERO;

        // ==================== PAYOUTS ====================
        List<PayoutMember> payouts = payoutMemberRepository.findPaidByMemberId(memberId);
        BigDecimal totalPaidOut = payouts.stream()
                .map(p -> p.getPayoutAmount() != null ? p.getPayoutAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        List<PayoutMemberDto> payoutHistory = payouts.stream()
                .map(pm -> PayoutMemberDto.builder()
                        .id(pm.getId())
                        .memberId(pm.getMember().getId())
                        .memberName(pm.getMember().getName())
                        .sharePercentage(pm.getSharePercentage())
                        .contributionAmount(pm.getContributionAmount())
                        .payoutAmount(pm.getPayoutAmount())
                        .status(pm.getStatus().name())
                        .paidAt(pm.getPaidAt() != null ? pm.getPaidAt().toString() : null)
                        .paymentReference(pm.getPaymentReference())
                        .build())
                .collect(Collectors.toList());

        // ==================== WELFARE ====================
        BigDecimal totalWelfareContributed = BigDecimal.ZERO;
        BigDecimal totalWelfareReceived = BigDecimal.ZERO;
        try {
            List<WelfareContribution> welfareContribs = welfareContributionRepository.findByMemberId(memberId);
            totalWelfareContributed = welfareContribs.stream()
                    .filter(w -> Boolean.TRUE.equals(w.getPaid()))
                    .map(w -> w.getAmountPaid() != null ? w.getAmountPaid() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            // Check welfare events where member was beneficiary
            List<WelfareEvent> welfareEvents = welfareEventRepository.findByMemberId(memberId);
            totalWelfareReceived = welfareEvents.stream()
                    .map(w -> w.getTotalCollected() != null ? w.getTotalCollected() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        } catch (Exception e) {
            log.debug("Error getting welfare data: {}", e.getMessage());
        }

        // ==================== MERRY-GO-ROUND ====================
        BigDecimal totalMgrContributed = BigDecimal.ZERO;
        BigDecimal totalMgrReceived = BigDecimal.ZERO;
        try {
            List<MerryGoRoundContribution> mgrContribs = mgrContributionRepository.findByMemberId(memberId);
            totalMgrContributed = mgrContribs.stream()
                    .filter(c -> c.getStatus() == MerryGoRoundContribution.ContributionStatus.PAID)
                    .map(c -> c.getAmount() != null ? c.getAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            // Check MGR rounds where member was recipient
            List<MerryGoRoundRound> mgrRounds = mgrRoundRepository.findByRecipientId(memberId);
            totalMgrReceived = mgrRounds.stream()
                    .filter(r -> r.getStatus() == MerryGoRoundRound.RoundStatus.COMPLETED 
                            || r.getStatus() == MerryGoRoundRound.RoundStatus.PAID)
                    .map(r -> r.getPaidAmount() != null ? r.getPaidAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        } catch (Exception e) {
            log.debug("Error getting MGR data: {}", e.getMessage());
        }

        // ==================== RECENT TRANSACTIONS ====================
        List<Transaction> transactions = transactionRepository.findTop10ByMemberIdOrderByDateDesc(memberId);
        List<TransactionDto> recentTransactions = transactions.stream()
                .map(t -> TransactionDto.builder()
                        .id(t.getId())
                        .date(t.getDate().toString())
                        .type(t.getType().name())
                        .description(t.getDescription())
                        .amount(t.getAmount())
                        .direction(t.getDirection().name())
                        .build())
                .collect(Collectors.toList());

        // ==================== NET POSITION ====================
        // Net Position = Current Share Value (what they own in the group) 
        //              - Outstanding Loans 
        //              - Unpaid Fines 
        //              - Unpaid Contributions
        // This represents "if they left today, what would they walk away with"
        BigDecimal currentShareValue = memberShare != null ? memberShare.getMemberValue() : BigDecimal.ZERO;
        BigDecimal unpaidContributions = totalContributionsDue.subtract(totalContributionsPaid);
        if (unpaidContributions.compareTo(BigDecimal.ZERO) < 0) {
            unpaidContributions = BigDecimal.ZERO; // Can't owe negative
        }
        BigDecimal unpaidFines = totalFinesImposed.subtract(totalFinesPaid);
        if (unpaidFines.compareTo(BigDecimal.ZERO) < 0) {
            unpaidFines = BigDecimal.ZERO;
        }
        BigDecimal netPosition = currentShareValue
                .subtract(outstandingLoanBalance)
                .subtract(unpaidFines)
                .subtract(unpaidContributions);

        // Calculate original contributions (before payout adjustments)
        BigDecimal originalContributions = totalContributionsPaid;

        return MemberFinancialSummaryDto.builder()
                .memberId(member.getId())
                .memberName(member.getName())
                .memberNo(member.getMemberNo())
                .email(member.getEmail())
                .phone(member.getPhone())
                .status(member.getStatus().name())
                .joinDate(member.getJoinDate() != null ? member.getJoinDate().toString() : null)
                
                // Share ownership
                .originalContributions(originalContributions)
                .adjustedContributions(memberShare != null ? memberShare.getContributionAmount() : BigDecimal.ZERO)
                .sharePercentage(memberShare != null ? memberShare.getSharePercentage() : BigDecimal.ZERO)
                .currentShareValue(memberShare != null ? memberShare.getMemberValue() : BigDecimal.ZERO)
                .totalPaidOut(totalPaidOut)
                
                // Contributions
                .totalContributionsDue(totalContributionsDue)
                .totalContributionsPaid(totalContributionsPaid)
                .contributionsBalance(totalContributionsDue.subtract(totalContributionsPaid))
                .contributionMonthsCount(contributions.size())
                .contributionsPaidCount(paidCount)
                .contributionsPendingCount(pendingCount)
                .contributionsByType(contributionsByType)
                
                // Loans
                .totalLoansBorrowed(totalLoansBorrowed)
                .totalLoansRepaid(totalLoansRepaid)
                .outstandingLoanBalance(outstandingLoanBalance)
                .activeLoansCount(activeLoansCount)
                .clearedLoansCount(clearedLoansCount)
                
                // Fines & Penalties
                .totalFinesImposed(totalFinesImposed)
                .totalFinesPaid(totalFinesPaid)
                .finesBalance(totalFinesImposed.subtract(totalFinesPaid))
                .totalPenaltiesPaid(totalPenaltiesPaid)
                
                // Welfare
                .totalWelfareContributed(totalWelfareContributed)
                .totalWelfareReceived(totalWelfareReceived)
                
                // MGR
                .totalMgrContributed(totalMgrContributed)
                .totalMgrReceived(totalMgrReceived)
                
                // Transactions & Payouts
                .recentTransactions(recentTransactions)
                .payoutHistory(payoutHistory)
                
                // Net position
                .netPosition(netPosition)
                .build();
    }
    
    /**
     * Get paginated transactions for a member.
     */
    @Transactional(readOnly = true)
    public PageResponse<TransactionDto> getMemberTransactionsPaginated(Long memberId, Long tenantId, String type, int page, int size) {
        // Verify member belongs to tenant
        memberRepository.findByIdAndTenantId(memberId, tenantId)
                .orElseThrow(() -> new RuntimeException("Member not found"));
        
        if (size <= 0) size = 10;
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                page, size, org.springframework.data.domain.Sort.by("date").descending());
        
        org.springframework.data.domain.Page<Transaction> transactionPage;
        
        if (type != null && !type.equalsIgnoreCase("ALL") && !type.trim().isEmpty()) {
            Transaction.TransactionType transactionType = Transaction.TransactionType.valueOf(type.toUpperCase());
            transactionPage = transactionRepository.findByMemberIdAndType(memberId, transactionType, pageable);
        } else {
            transactionPage = transactionRepository.findByMemberIdOrderByDateDesc(memberId, pageable);
        }
        
        org.springframework.data.domain.Page<TransactionDto> dtoPage = transactionPage.map(t -> TransactionDto.builder()
                .id(t.getId())
                .date(t.getDate().toString())
                .type(t.getType().name())
                .description(t.getDescription())
                .amount(t.getAmount())
                .direction(t.getDirection().name())
                .build());
        return PageResponse.from(dtoPage);
    }

    /**
     * Reset a member's password (admin action).
     * Sets the password to the specified value and forces the member to change it on next login.
     */
    @Transactional
    public void resetPassword(Long memberId, String newPassword, Long tenantId) {
        Member member = memberRepository.findByIdAndTenantId(memberId, tenantId)
                .orElseThrow(() -> new RuntimeException("Member not found"));
        
        log.info("Resetting password for member {} (ID: {})", member.getName(), memberId);
        
        // Set the new password (encoded)
        member.setPassword(passwordEncoder.encode(newPassword));
        
        // Force password change on next login
        member.setMustChangePassword(true);
        
        memberRepository.save(member);
        
        // Send password reset email
        if (member.getEmail() != null && !member.getEmail().isEmpty()) {
            emailService.sendPasswordResetEmail(
                    member.getEmail(),
                    member.getName(),
                    newPassword
            );
        }
        
        log.info("Password reset completed for member {} - email sent, mustChangePassword set to true", member.getName());
    }

    private MemberDto toDto(Member member) {
        return MemberDto.builder()
                .id(member.getId())
                .name(member.getName())
                .phone(member.getPhone())
                .email(member.getEmail())
                .memberNo(member.getMemberNo())
                .joinDate(member.getJoinDate() != null ? member.getJoinDate().toString() : null)
                .status(member.getStatus().name())
                .role(member.getRole() != null ? member.getRole().getName() : "Member")
                .roleId(member.getRole() != null ? member.getRole().getId() : null)
                .totalSavings(member.getTotalSavings())
                .totalShares(member.getTotalShares())
                .outstandingLoan(member.getOutstandingLoan())
                .arrearsMonths(member.getArrearsMonths())
                .build();
    }
}
