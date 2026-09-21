package com.calpex_technologies.chama.service;

import com.calpex_technologies.chama.dto.RegistrationFeePaymentDto;
import com.calpex_technologies.chama.entity.*;
import com.calpex_technologies.chama.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class RegistrationFeeService {

    private final RegistrationFeePaymentRepository paymentRepository;
    private final MemberRepository memberRepository;
    private final TenantRepository tenantRepository;
    private final TransactionRepository transactionRepository;
    private final PayableRepository payableRepository;

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * Get registration fee amount for the tenant
     */
    public BigDecimal getRegistrationFeeAmount(Long tenantId) {
        if (tenantId == null) {
            return BigDecimal.ZERO;
        }
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));
        return tenant.getRegistrationFee() != null ? tenant.getRegistrationFee() : BigDecimal.ZERO;
    }

    /**
     * Check if registration fee is required (explicitly enabled and amount > 0)
     */
    public boolean isRegistrationFeeRequired(Long tenantId) {
        if (tenantId == null) {
            return false;
        }
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null) {
            return false;
        }
        // Check explicit flag and amount > 0
        boolean flagEnabled = tenant.getRegistrationFeeRequired() != null && tenant.getRegistrationFeeRequired();
        BigDecimal fee = tenant.getRegistrationFee();
        boolean hasAmount = fee != null && fee.compareTo(BigDecimal.ZERO) > 0;
        return flagEnabled && hasAmount;
    }

    /**
     * Get registration fee status for a member
     */
    public RegistrationFeePaymentDto getMemberRegistrationFeeStatus(Long tenantId, Long memberId) {
        // If no registration fee required or memberId is null, return null
        if (memberId == null || !isRegistrationFeeRequired(tenantId)) {
            return null;
        }

        return paymentRepository.findByTenantIdAndMemberId(tenantId, memberId)
                .map(this::toDto)
                .orElse(createPendingDto(tenantId, memberId));
    }

    /**
     * Get all registration fee payments for a tenant (with payableId lookup)
     */
    public List<RegistrationFeePaymentDto> getAllPayments(Long tenantId) {
        List<RegistrationFeePayment> payments = paymentRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        
        // Batch lookup payables with AWAITING_CONFIRMATION status for REGISTRATION_FEE type
        List<Payable> pendingPayables = payableRepository.findByTenant_IdAndPayableTypeAndStatus(
                tenantId, PayableType.REGISTRATION_FEE, PayableStatus.AWAITING_CONFIRMATION);
        
        // Map: memberId -> payableId
        java.util.Map<Long, Long> memberToPayableId = pendingPayables.stream()
                .collect(Collectors.toMap(
                        p -> p.getMember().getId(),
                        Payable::getId,
                        (existing, replacement) -> existing // keep first if duplicate
                ));
        
        return payments.stream()
                .map(payment -> {
                    RegistrationFeePaymentDto dto = toDto(payment);
                    Long memberId = payment.getMember().getId();
                    if (memberToPayableId.containsKey(memberId)) {
                        dto.setHasPendingClaim(true);
                        dto.setPayableId(memberToPayableId.get(memberId));
                    } else {
                        dto.setHasPendingClaim(false);
                    }
                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * Get payments awaiting verification (for admin view)
     */
    public List<RegistrationFeePaymentDto> getAwaitingVerification(Long tenantId) {
        return paymentRepository.findAwaitingVerification(tenantId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Count payments awaiting verification
     */
    public long countAwaitingVerification(Long tenantId) {
        return paymentRepository.countAwaitingVerification(tenantId);
    }

    /**
     * Submit registration fee payment (member pays)
     */
    @Transactional
    public RegistrationFeePaymentDto submitPayment(Long tenantId, Long memberId, String referenceNumber) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        if (!isRegistrationFeeRequired(tenantId)) {
            throw new RuntimeException("Registration fee is not required for this chama");
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        // Check if already has a verified payment
        if (paymentRepository.existsByTenantIdAndMemberIdAndStatus(tenantId, memberId, 
                RegistrationFeePayment.PaymentStatus.VERIFIED)) {
            throw new RuntimeException("Registration fee already paid and verified");
        }

        // Check if already awaiting verification
        if (paymentRepository.existsByTenantIdAndMemberIdAndStatus(tenantId, memberId, 
                RegistrationFeePayment.PaymentStatus.AWAITING_VERIFICATION)) {
            throw new RuntimeException("Payment already submitted and awaiting verification");
        }

        // Find existing pending/rejected payment or create new
        RegistrationFeePayment payment = paymentRepository.findByTenantIdAndMemberId(tenantId, memberId)
                .orElse(RegistrationFeePayment.builder()
                        .tenant(tenant)
                        .member(member)
                        .amount(tenant.getRegistrationFee())
                        .build());

        payment.setReferenceNumber(referenceNumber);
        payment.setStatus(RegistrationFeePayment.PaymentStatus.AWAITING_VERIFICATION);
        payment.setPaidAt(LocalDateTime.now());
        payment.setAmount(tenant.getRegistrationFee());
        // Clear any previous rejection
        payment.setRejectionReason(null);
        payment.setRejectedAt(null);
        payment.setRejectedBy(null);

        payment = paymentRepository.save(payment);
        log.info("Registration fee payment submitted by member {} with ref {}", memberId, referenceNumber);

        return toDto(payment);
    }

    /**
     * Verify (confirm) registration fee payment (admin action)
     */
    @Transactional
    public RegistrationFeePaymentDto verifyPayment(Long tenantId, Long paymentId, Long verifiedByMemberId) {
        RegistrationFeePayment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        if (!payment.getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("Payment not found");
        }

        if (payment.getStatus() != RegistrationFeePayment.PaymentStatus.AWAITING_VERIFICATION) {
            throw new RuntimeException("Payment is not awaiting verification");
        }

        Member verifiedBy = memberRepository.findById(verifiedByMemberId)
                .orElseThrow(() -> new RuntimeException("Verifier member not found"));

        payment.setStatus(RegistrationFeePayment.PaymentStatus.VERIFIED);
        payment.setVerifiedAt(LocalDateTime.now());
        payment.setVerifiedBy(verifiedBy);

        payment = paymentRepository.save(payment);

        // Create GL transaction
        String description = String.format("Registration fee - %s (Ref: %s)", 
                payment.getMember().getName(), payment.getReferenceNumber());
        Transaction transaction = Transaction.builder()
                .tenant(payment.getTenant())
                .date(LocalDate.now())
                .type(Transaction.TransactionType.REGISTRATION_FEE)
                .description(description)
                .amount(payment.getAmount())
                .direction(Transaction.TransactionDirection.IN)
                .member(payment.getMember())
                .referenceId(payment.getId())
                .referenceType("REGISTRATION_FEE")
                .build();
        transactionRepository.save(transaction);

        log.info("Registration fee payment {} verified by member {}", paymentId, verifiedByMemberId);

        return toDto(payment);
    }

    /**
     * Reject registration fee payment (admin action)
     */
    @Transactional
    public RegistrationFeePaymentDto rejectPayment(Long tenantId, Long paymentId, Long rejectedByMemberId, String reason) {
        RegistrationFeePayment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        if (!payment.getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("Payment not found");
        }

        if (payment.getStatus() != RegistrationFeePayment.PaymentStatus.AWAITING_VERIFICATION) {
            throw new RuntimeException("Payment is not awaiting verification");
        }

        Member rejectedBy = memberRepository.findById(rejectedByMemberId)
                .orElseThrow(() -> new RuntimeException("Rejector member not found"));

        payment.setStatus(RegistrationFeePayment.PaymentStatus.REJECTED);
        payment.setRejectedAt(LocalDateTime.now());
        payment.setRejectedBy(rejectedBy);
        payment.setRejectionReason(reason);

        payment = paymentRepository.save(payment);
        log.info("Registration fee payment {} rejected by member {} reason: {}", paymentId, rejectedByMemberId, reason);

        return toDto(payment);
    }

    /**
     * Create or ensure registration fee record exists for a new member
     */
    @Transactional
    public void ensureRegistrationFeeRecord(Long tenantId, Long memberId) {
        if (!isRegistrationFeeRequired(tenantId)) {
            return;
        }

        // Only create if doesn't exist
        if (paymentRepository.findByTenantIdAndMemberId(tenantId, memberId).isEmpty()) {
            Tenant tenant = tenantRepository.findById(tenantId)
                    .orElseThrow(() -> new RuntimeException("Tenant not found"));
            Member member = memberRepository.findById(memberId)
                    .orElseThrow(() -> new RuntimeException("Member not found"));

            RegistrationFeePayment payment = RegistrationFeePayment.builder()
                    .tenant(tenant)
                    .member(member)
                    .amount(tenant.getRegistrationFee())
                    .status(RegistrationFeePayment.PaymentStatus.PENDING)
                    .build();
            paymentRepository.save(payment);
            log.info("Created registration fee record for new member {}", memberId);
        }
    }

    private RegistrationFeePaymentDto createPendingDto(Long tenantId, Long memberId) {
        if (memberId == null) {
            return null;
        }
        Member member = memberRepository.findById(memberId).orElse(null);
        BigDecimal amount = getRegistrationFeeAmount(tenantId);
        
        return RegistrationFeePaymentDto.builder()
                .memberId(memberId)
                .memberName(member != null ? member.getName() : null)
                .memberNo(member != null ? member.getMemberNo() : null)
                .amount(amount)
                .status("PENDING")
                .build();
    }

    private RegistrationFeePaymentDto toDto(RegistrationFeePayment payment) {
        return RegistrationFeePaymentDto.builder()
                .id(payment.getId())
                .memberId(payment.getMember().getId())
                .memberName(payment.getMember().getName())
                .memberNo(payment.getMember().getMemberNo())
                .amount(payment.getAmount())
                .referenceNumber(payment.getReferenceNumber())
                .status(payment.getStatus().name())
                .paidAt(payment.getPaidAt() != null ? payment.getPaidAt().format(DATETIME_FORMATTER) : null)
                .verifiedAt(payment.getVerifiedAt() != null ? payment.getVerifiedAt().format(DATETIME_FORMATTER) : null)
                .verifiedByName(payment.getVerifiedBy() != null ? payment.getVerifiedBy().getName() : null)
                .rejectionReason(payment.getRejectionReason())
                .rejectedAt(payment.getRejectedAt() != null ? payment.getRejectedAt().format(DATETIME_FORMATTER) : null)
                .rejectedByName(payment.getRejectedBy() != null ? payment.getRejectedBy().getName() : null)
                .build();
    }
}
