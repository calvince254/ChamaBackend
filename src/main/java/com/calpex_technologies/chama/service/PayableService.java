package com.calpex_technologies.chama.service;

import com.calpex_technologies.chama.dto.PayableDto;
import com.calpex_technologies.chama.entity.*;
import com.calpex_technologies.chama.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for managing Payables - universal payment abstractions.
 * Each module (contributions, loans, welfare, etc.) creates payables that can be paid through
 * the unified payment system.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PayableService {

    private final PayableRepository payableRepository;
    private final MemberRepository memberRepository;
    private final TenantRepository tenantRepository;
    private final PaymentSettingsService paymentSettingsService;

    /**
     * Create or update a payable for a module-specific due.
     * If a payable already exists for the same type and reference ID, update it.
     */
    @Transactional
    public Payable createOrUpdatePayable(Long tenantId, PayableType type, Long refId, Long memberId,
                                          BigDecimal amount, String description, LocalDate dueDate) {
        // Check if payable already exists
        Optional<Payable> existing = payableRepository.findByPayableTypeAndPayableRefIdAndTenant_Id(type, refId, tenantId);
        
        if (existing.isPresent()) {
            Payable payable = existing.get();
            // Only update if status is still OUTSTANDING
            if (payable.getStatus() == PayableStatus.OUTSTANDING) {
                payable.setAmount(amount);
                payable.setDescription(description);
                payable.setDueDate(dueDate);
                return payableRepository.save(payable);
            }
            return payable;
        }

        // Create new payable
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found: " + memberId));
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found: " + tenantId));

        // Get treasurer info from payment settings
        PaymentSettings settings = paymentSettingsService.getOrCreateSettings(tenantId);

        Payable payable = Payable.builder()
                .tenant(tenant)
                .payableType(type)
                .payableRefId(refId)
                .member(member)
                .amount(amount)
                .recipientNumber(settings.getTreasurerPhone())
                .recipientName(settings.getTreasurerName())
                .status(PayableStatus.OUTSTANDING)
                .description(description)
                .dueDate(dueDate)
                .build();

        Payable saved = payableRepository.save(payable);
        log.debug("Created payable {} for {} #{}", saved.getId(), type, refId);
        return saved;
    }

    /**
     * Get payable by ID.
     */
    @Transactional(readOnly = true)
    public PayableDto getPayable(Long id, Long tenantId) {
        Payable payable = payableRepository.findByIdAndTenant_Id(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Payable not found: " + id));
        return toDto(payable);
    }

    /**
     * Get all outstanding payables for a member.
     */
    @Transactional(readOnly = true)
    public List<PayableDto> getOutstandingPayablesForMember(Long memberId) {
        return payableRepository.findByMember_IdAndStatusOrderByDueDateAsc(memberId, PayableStatus.OUTSTANDING)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Get all payables for a member.
     */
    @Transactional(readOnly = true)
    public List<PayableDto> getPayablesForMember(Long memberId, Long tenantId) {
        return payableRepository.findByMember_IdAndTenant_IdOrderByCreatedAtDesc(memberId, tenantId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Get all payables for a tenant with pagination.
     */
    @Transactional(readOnly = true)
    public Page<PayableDto> getAllPayables(Long tenantId, Pageable pageable) {
        return payableRepository.findByTenant_IdOrderByCreatedAtDesc(tenantId, pageable)
                .map(this::toDto);
    }

    /**
     * Get payables by type for a tenant.
     */
    @Transactional(readOnly = true)
    public Page<PayableDto> getPayablesByType(Long tenantId, PayableType type, Pageable pageable) {
        return payableRepository.findByTenantIdAndPayableType(tenantId, type, pageable)
                .map(this::toDto);
    }

    /**
     * Get payables awaiting confirmation.
     */
    @Transactional(readOnly = true)
    public List<PayableDto> getAwaitingConfirmation(Long tenantId) {
        return payableRepository.findByTenant_IdAndStatusOrderByUpdatedAtDesc(tenantId, PayableStatus.AWAITING_CONFIRMATION)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Find payable by module type and reference ID.
     */
    @Transactional(readOnly = true)
    public Optional<Payable> findByTypeAndRefId(PayableType type, Long refId, Long tenantId) {
        return payableRepository.findByPayableTypeAndPayableRefIdAndTenant_Id(type, refId, tenantId);
    }

    /**
     * Get payable entity by ID (internal use).
     */
    @Transactional(readOnly = true)
    public Payable getPayableEntity(Long id) {
        return payableRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Payable not found: " + id));
    }

    /**
     * Update payable status.
     */
    @Transactional
    public Payable updateStatus(Long payableId, PayableStatus status) {
        Payable payable = payableRepository.findById(payableId)
                .orElseThrow(() -> new RuntimeException("Payable not found: " + payableId));
        payable.setStatus(status);
        return payableRepository.save(payable);
    }

    /**
     * Mark payable as paid.
     * This is called when a payment is confirmed through any of the 3 methods.
     */
    @Transactional
    public void markAsPaid(Long payableId) {
        Payable payable = payableRepository.findById(payableId)
                .orElseThrow(() -> new RuntimeException("Payable not found: " + payableId));
        
        payable.setStatus(PayableStatus.PAID);
        payableRepository.save(payable);
        
        // Trigger module-specific update
        updateModuleRecord(payable);
        
        log.info("Payable {} marked as PAID", payableId);
    }

    /**
     * Mark payable as awaiting confirmation.
     */
    @Transactional
    public void markAwaitingConfirmation(Long payableId) {
        updateStatus(payableId, PayableStatus.AWAITING_CONFIRMATION);
    }

    /**
     * Cancel a payable (e.g., when claim expires).
     */
    @Transactional
    public void markExpired(Long payableId) {
        Payable payable = payableRepository.findById(payableId)
                .orElseThrow(() -> new RuntimeException("Payable not found: " + payableId));
        
        // Only mark as expired if it was awaiting confirmation
        if (payable.getStatus() == PayableStatus.AWAITING_CONFIRMATION) {
            payable.setStatus(PayableStatus.OUTSTANDING);  // Back to outstanding
            payableRepository.save(payable);
        }
    }

    /**
     * Count outstanding payables for a member.
     */
    @Transactional(readOnly = true)
    public long countOutstandingForMember(Long memberId) {
        return payableRepository.countByMember_IdAndStatus(memberId, PayableStatus.OUTSTANDING);
    }

    /**
     * Delete payable when the underlying record is deleted.
     */
    @Transactional
    public void deletePayable(PayableType type, Long refId, Long tenantId) {
        payableRepository.findByPayableTypeAndPayableRefIdAndTenant_Id(type, refId, tenantId)
                .ifPresent(payable -> {
                    if (payable.getStatus() == PayableStatus.OUTSTANDING) {
                        payableRepository.delete(payable);
                        log.debug("Deleted payable for {} #{}", type, refId);
                    }
                });
    }

    /**
     * Update the module-specific record when payment is confirmed.
     * This is the hook that integrates back to each module.
     */
    private void updateModuleRecord(Payable payable) {
        // This will be enhanced to call module-specific services
        // For now, logging the action
        log.info("Module update needed for {} #{} - Payment confirmed", 
                payable.getPayableType(), payable.getPayableRefId());
        
        // The actual integration will be done through events or direct service calls:
        // - CONTRIBUTION: Update contribution status to PAID
        // - LOAN_REPAYMENT: Record loan repayment
        // - WELFARE_DUE: Mark welfare contribution as paid
        // - MGR_CONTRIBUTION: Mark MGR contribution as paid
        // - etc.
    }

    /**
     * Convert Payable entity to DTO.
     */
    public PayableDto toDto(Payable payable) {
        String moduleName = getModuleName(payable.getPayableType());
        String shortDesc = generateShortDescription(payable);

        return PayableDto.builder()
                .id(payable.getId())
                .payableType(payable.getPayableType().name())
                .payableRefId(payable.getPayableRefId())
                .memberId(payable.getMember().getId())
                .memberName(payable.getMember().getName())
                .memberPhone(payable.getMember().getPhone())
                .amount(payable.getAmount())
                .recipientNumber(payable.getRecipientNumber())
                .recipientName(payable.getRecipientName())
                .status(payable.getStatus().name())
                .description(payable.getDescription())
                .dueDate(payable.getDueDate())
                .createdAt(payable.getCreatedAt())
                .updatedAt(payable.getUpdatedAt())
                .moduleName(moduleName)
                .shortDescription(shortDesc)
                .build();
    }

    private String getModuleName(PayableType type) {
        return switch (type) {
            case CONTRIBUTION -> "Contributions";
            case LOAN_REPAYMENT -> "Loans";
            case WELFARE_DUE -> "Welfare";
            case MGR_CONTRIBUTION -> "Merry-Go-Round";
            case MGR_FINE -> "Merry-Go-Round Fines";
            case SHARE_PURCHASE -> "Shares";
            case INVESTMENT_BUYIN -> "Investments";
            case LICENSE_INVOICE -> "Billing";
            case REGISTRATION_FEE -> "Registration";
            case PENALTY -> "Penalties";
            case LOAN_PENALTY -> "Loan Penalties";
            case MEETING_FINE -> "Meeting Fines";
        };
    }

    private String generateShortDescription(Payable payable) {
        if (payable.getDescription() != null && !payable.getDescription().isBlank()) {
            // Truncate if too long
            if (payable.getDescription().length() > 50) {
                return payable.getDescription().substring(0, 47) + "...";
            }
            return payable.getDescription();
        }
        return getModuleName(payable.getPayableType()) + " payment";
    }
}
