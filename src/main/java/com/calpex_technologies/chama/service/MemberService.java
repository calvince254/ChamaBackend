package com.calpex_technologies.chama.service;

import com.calpex_technologies.chama.dto.*;
import com.calpex_technologies.chama.entity.*;
import com.calpex_technologies.chama.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Transactional(readOnly = true)
    public List<MemberDto> getAllMembers(Long tenantId) {
        log.info("MemberService.getAllMembers called with tenantId={}", tenantId);
        List<Member> members = memberRepository.findByTenantIdWithRole(tenantId);
        log.info("Found {} members for tenantId={}", members.size(), tenantId);
        return members.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
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

        Member member = Member.builder()
                .name(request.getName())
                .phone(request.getPhone())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword() != null ? request.getPassword() : "password123"))
                .memberNo(memberNo)
                .joinDate(LocalDate.now())
                .status(Member.MemberStatus.ACTIVE)
                .role(role)
                .tenant(tenant)
                .build();

        member = memberRepository.save(member);
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
                .totalSavings(member.getTotalSavings())
                .totalShares(member.getTotalShares())
                .outstandingLoan(member.getOutstandingLoan())
                .arrearsMonths(member.getArrearsMonths())
                .build();
    }
}
