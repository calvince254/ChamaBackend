package com.calpex_technologies.chama.service;

import com.calpex_technologies.chama.dto.LoanPenaltyDto;
import com.calpex_technologies.chama.entity.LoanPenalty;
import com.calpex_technologies.chama.entity.Member;
import com.calpex_technologies.chama.entity.Payable;
import com.calpex_technologies.chama.entity.PayableStatus;
import com.calpex_technologies.chama.entity.PayableType;
import com.calpex_technologies.chama.entity.Transaction;
import com.calpex_technologies.chama.repository.LoanPenaltyRepository;
import com.calpex_technologies.chama.repository.MemberRepository;
import com.calpex_technologies.chama.repository.PayableRepository;
import com.calpex_technologies.chama.repository.PaymentClaimRepository;
import com.calpex_technologies.chama.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class LoanPenaltyService {

    private final LoanPenaltyRepository penaltyRepository;
    private final MemberRepository memberRepository;
    private final TransactionRepository transactionRepository;
    private final PayableRepository payableRepository;
    private final PaymentClaimRepository paymentClaimRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * Get all penalties for a tenant (admin view).
     */
    public List<LoanPenaltyDto> getAllPenalties(Long tenantId) {
        List<LoanPenalty> penalties = penaltyRepository.findByTenantIdOrderByImposedAtDesc(tenantId);
        return toDtoListWithPendingClaims(penalties, tenantId);
    }

    /**
     * Get penalties for a specific member (member's own penalties).
     */
    public List<LoanPenaltyDto> getMyPenalties(Long tenantId, Long memberId) {
        List<LoanPenalty> penalties = penaltyRepository.findByMemberIdOrderByImposedAtDesc(memberId).stream()
                .filter(p -> p.getTenant().getId().equals(tenantId))
                .collect(Collectors.toList());
        return toDtoListWithPendingClaims(penalties, tenantId);
    }

    /**
     * Get penalties for a specific loan.
     */
    public List<LoanPenaltyDto> getPenaltiesForLoan(Long loanId) {
        List<LoanPenalty> penalties = penaltyRepository.findByLoanIdOrderByImposedAtDesc(loanId);
        if (penalties.isEmpty()) return List.of();
        Long tenantId = penalties.get(0).getTenant().getId();
        return toDtoListWithPendingClaims(penalties, tenantId);
    }

    /**
     * Get a single penalty by ID.
     */
    public LoanPenaltyDto getPenalty(Long id, Long tenantId) {
        LoanPenalty penalty = penaltyRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Penalty not found"));
        return toDto(penalty);
    }

    /**
     * Waive a penalty (admin only).
     */
    @Transactional
    public LoanPenaltyDto waivePenalty(Long id, Long tenantId, Long adminMemberId, String reason) {
        LoanPenalty penalty = penaltyRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Penalty not found"));

        if (penalty.getStatus() == LoanPenalty.PenaltyStatus.PAID) {
            throw new RuntimeException("Cannot waive a paid penalty");
        }
        if (penalty.getStatus() == LoanPenalty.PenaltyStatus.WAIVED) {
            throw new RuntimeException("Penalty is already waived");
        }

        Member admin = memberRepository.findById(adminMemberId)
                .orElseThrow(() -> new RuntimeException("Admin member not found"));

        penalty.setStatus(LoanPenalty.PenaltyStatus.WAIVED);
        penalty.setWaivedAt(LocalDateTime.now());
        penalty.setWaivedBy(admin);
        penalty.setWaiveReason(reason);

        penalty = penaltyRepository.save(penalty);
        log.info("Penalty {} waived by admin {} for reason: {}", id, admin.getName(), reason);

        return toDto(penalty);
    }

    /**
     * Mark a penalty as paid (partial or full).
     */
    @Transactional
    public LoanPenaltyDto recordPayment(Long id, Long tenantId, BigDecimal amount) {
        LoanPenalty penalty = penaltyRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Penalty not found"));

        if (penalty.getStatus() == LoanPenalty.PenaltyStatus.PAID) {
            throw new RuntimeException("Penalty is already fully paid");
        }
        if (penalty.getStatus() == LoanPenalty.PenaltyStatus.WAIVED) {
            throw new RuntimeException("Cannot pay a waived penalty");
        }

        BigDecimal currentPaid = penalty.getPaidAmount() != null ? penalty.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal newPaid = currentPaid.add(amount);
        BigDecimal totalDue = penalty.getAmount();

        penalty.setPaidAmount(newPaid);

        if (newPaid.compareTo(totalDue) >= 0) {
            penalty.setStatus(LoanPenalty.PenaltyStatus.PAID);
            penalty.setPaidAt(LocalDateTime.now());
            log.info("Penalty {} fully paid", id);
        } else {
            penalty.setStatus(LoanPenalty.PenaltyStatus.PARTIAL);
            log.info("Penalty {} partially paid. {} of {} paid", id, newPaid, totalDue);
        }

        penalty = penaltyRepository.save(penalty);
        
        // Create GL transaction for loan penalty payment
        String description = String.format("Loan penalty payment - %s (%s)", 
                penalty.getMember().getName(), penalty.getReason());
        Transaction transaction = Transaction.builder()
                .tenant(penalty.getTenant())
                .date(LocalDate.now())
                .type(Transaction.TransactionType.PENALTY)
                .description(description)
                .amount(amount)
                .direction(Transaction.TransactionDirection.IN)
                .member(penalty.getMember())
                .referenceId(penalty.getId())
                .referenceType("LOAN_PENALTY")
                .build();
        transactionRepository.save(transaction);
        log.info("Created GL transaction for loan penalty payment: {} amount {}", id, amount);

        return toDto(penalty);
    }

    /**
     * Get summary statistics for penalties.
     */
    public PenaltySummary getPenaltySummary(Long tenantId) {
        BigDecimal totalPenalties = penaltyRepository.getTotalPenaltiesForTenant(tenantId);
        BigDecimal totalPaid = penaltyRepository.getTotalPaidPenaltiesForTenant(tenantId);
        
        List<LoanPenalty> unpaid = penaltyRepository.findByTenantIdAndStatus(tenantId, LoanPenalty.PenaltyStatus.UNPAID);
        List<LoanPenalty> partial = penaltyRepository.findByTenantIdAndStatus(tenantId, LoanPenalty.PenaltyStatus.PARTIAL);

        return PenaltySummary.builder()
                .totalPenalties(totalPenalties)
                .totalPaid(totalPaid)
                .totalOutstanding(totalPenalties.subtract(totalPaid))
                .unpaidCount(unpaid.size())
                .partialCount(partial.size())
                .build();
    }

    private LoanPenaltyDto toDto(LoanPenalty penalty) {
        // For single penalty lookup, do individual query
        boolean hasPendingClaim = false;
        Long payableId = null;
        if (penalty.getStatus() != LoanPenalty.PenaltyStatus.PAID && penalty.getStatus() != LoanPenalty.PenaltyStatus.WAIVED) {
            var payableOpt = payableRepository.findByPayableTypeAndPayableRefIdAndTenant_Id(
                    PayableType.LOAN_PENALTY, penalty.getId(), penalty.getTenant().getId());
            if (payableOpt.isPresent()) {
                Payable payable = payableOpt.get();
                hasPendingClaim = payable.getStatus() == PayableStatus.AWAITING_CONFIRMATION;
                if (hasPendingClaim) {
                    payableId = payable.getId();
                }
            }
        }
        return toDto(penalty, hasPendingClaim, payableId);
    }
    
    private LoanPenaltyDto toDto(LoanPenalty penalty, boolean hasPendingClaim) {
        return toDto(penalty, hasPendingClaim, null);
    }
    
    private LoanPenaltyDto toDto(LoanPenalty penalty, boolean hasPendingClaim, Long payableId) {
        return LoanPenaltyDto.builder()
                .id(penalty.getId())
                .loanId(penalty.getLoan().getId())
                .memberId(penalty.getMember().getId())
                .memberName(penalty.getMember().getName())
                .memberNo(penalty.getMember().getMemberNo())
                .loanProductName(penalty.getLoan().getLoanProduct() != null ? 
                        penalty.getLoan().getLoanProduct().getName() : null)
                .amount(penalty.getAmount())
                .paidAmount(penalty.getPaidAmount())
                .balance(penalty.getBalance())
                .status(penalty.getStatus().name())
                .reason(penalty.getReason())
                .dueDate(penalty.getDueDate() != null ? penalty.getDueDate().format(DATE_FORMATTER) : null)
                .periodNumber(penalty.getPeriodNumber())
                .daysOverdue(penalty.getDaysOverdue())
                .autoGenerated(penalty.isAutoGenerated())
                .imposedAt(penalty.getImposedAt() != null ? penalty.getImposedAt().format(DATETIME_FORMATTER) : null)
                .paidAt(penalty.getPaidAt() != null ? penalty.getPaidAt().format(DATETIME_FORMATTER) : null)
                .waivedAt(penalty.getWaivedAt() != null ? penalty.getWaivedAt().format(DATETIME_FORMATTER) : null)
                .waivedByName(penalty.getWaivedBy() != null ? penalty.getWaivedBy().getName() : null)
                .waiveReason(penalty.getWaiveReason())
                .hasPendingClaim(hasPendingClaim)
                .payableId(payableId)
                .build();
    }
    
    /**
     * Convert list of loan penalties to DTOs with batch pending claim lookup.
     */
    private List<LoanPenaltyDto> toDtoListWithPendingClaims(List<LoanPenalty> penalties, Long tenantId) {
        if (penalties.isEmpty()) return List.of();
        
        List<Long> unpaidIds = penalties.stream()
                .filter(p -> p.getStatus() != LoanPenalty.PenaltyStatus.PAID && p.getStatus() != LoanPenalty.PenaltyStatus.WAIVED)
                .map(LoanPenalty::getId)
                .collect(Collectors.toList());
        
        // Batch fetch payables with pending claims (maps refId -> payableId)
        Map<Long, Long> pendingClaimPayableIds = new java.util.HashMap<>();
        if (!unpaidIds.isEmpty()) {
            List<Payable> pendingPayables = payableRepository.findByTenant_IdAndPayableTypeAndPayableRefIdInAndStatus(
                    tenantId, PayableType.LOAN_PENALTY, unpaidIds, PayableStatus.AWAITING_CONFIRMATION);
            for (Payable p : pendingPayables) {
                pendingClaimPayableIds.put(p.getPayableRefId(), p.getId());
            }
        }
        
        return penalties.stream()
                .map(p -> {
                    boolean hasPending = pendingClaimPayableIds.containsKey(p.getId());
                    Long pId = hasPending ? pendingClaimPayableIds.get(p.getId()) : null;
                    return toDto(p, hasPending, pId);
                })
                .collect(Collectors.toList());
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PenaltySummary {
        private BigDecimal totalPenalties;
        private BigDecimal totalPaid;
        private BigDecimal totalOutstanding;
        private int unpaidCount;
        private int partialCount;
    }
}
