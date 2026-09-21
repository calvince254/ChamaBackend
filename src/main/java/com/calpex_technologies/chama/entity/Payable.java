package com.calpex_technologies.chama.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * Universal payment abstraction.
 * Links any module's due (contribution, loan, welfare, etc.) to a single payable entity.
 * This allows the same payment flow to work across all modules.
 */
@Entity
@Table(name = "payable", indexes = {
    @Index(name = "idx_payable_tenant", columnList = "tenant_id"),
    @Index(name = "idx_payable_member", columnList = "member_id"),
    @Index(name = "idx_payable_type_ref", columnList = "payable_type, payable_ref_id"),
    @Index(name = "idx_payable_status", columnList = "status")
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Payable extends TenantAwareEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "payable_type", nullable = false, length = 30)
    private PayableType payableType;

    /**
     * ID of the actual record in the module-specific table.
     * E.g., contribution.id, loan.id, welfare_contribution.id, etc.
     */
    @Column(name = "payable_ref_id", nullable = false)
    private Long payableRefId;

    /**
     * The member who owes this payment.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    /**
     * Amount to be paid.
     */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /**
     * Recipient phone number for the payment.
     * For member-facing dues: treasurer's number.
     * For license invoices: platform's collection number.
     */
    @Column(name = "recipient_number", length = 15)
    private String recipientNumber;

    /**
     * Recipient name for display purposes.
     */
    @Column(name = "recipient_name", length = 100)
    private String recipientName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private PayableStatus status = PayableStatus.OUTSTANDING;

    /**
     * Description of what this payment is for (e.g., "January 2024 Monthly Contribution").
     */
    @Column(length = 255)
    private String description;

    /**
     * Optional due date for the payment.
     */
    @Column(name = "due_date")
    private java.time.LocalDate dueDate;
}
