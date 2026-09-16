package com.calpex_technologies.chama.service;

import com.calpex_technologies.chama.dto.InvoiceDto;
import com.calpex_technologies.chama.entity.*;
import com.calpex_technologies.chama.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LicenseService {

    private final LicenseRepository licenseRepository;
    private final LicenseTransactionRepository transactionRepository;
    private final InvoiceRepository invoiceRepository;
    private final TenantRepository tenantRepository;

    // ==================== LICENSE MANAGEMENT ====================

    /**
     * Create a new license for a tenant
     */
    @Transactional
    public License createLicense(Long tenantId, License license) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        // Check if license already exists
        if (licenseRepository.findByTenantId(tenantId).isPresent()) {
            throw new RuntimeException("License already exists for this tenant");
        }

        license.setTenant(tenant);
        if (license.getStatus() == null) {
            license.setStatus(License.LicenseStatus.TRIAL);
        }
        if (license.getBillingDayOfMonth() == null) {
            license.setBillingDayOfMonth(1); // Default to 1st of month
        }
        if (license.getGracePeriodDays() == null) {
            license.setGracePeriodDays(7); // Default 7 days grace
        }

        return licenseRepository.save(license);
    }

    /**
     * Update license settings
     */
    @Transactional
    public License updateLicense(Long licenseId, License updates) {
        License license = licenseRepository.findById(licenseId)
                .orElseThrow(() -> new RuntimeException("License not found"));

        if (updates.getContributionFeePercent() != null) {
            license.setContributionFeePercent(updates.getContributionFeePercent());
        }
        if (updates.getFineFeePercent() != null) {
            license.setFineFeePercent(updates.getFineFeePercent());
        }
        if (updates.getLoanFeePercent() != null) {
            license.setLoanFeePercent(updates.getLoanFeePercent());
        }
        if (updates.getPenaltyFeePercent() != null) {
            license.setPenaltyFeePercent(updates.getPenaltyFeePercent());
        }
        if (updates.getMonthlyFixedFee() != null) {
            license.setMonthlyFixedFee(updates.getMonthlyFixedFee());
        }
        if (updates.getGracePeriodDays() != null) {
            license.setGracePeriodDays(updates.getGracePeriodDays());
        }
        if (updates.getBillingDayOfMonth() != null) {
            license.setBillingDayOfMonth(updates.getBillingDayOfMonth());
        }
        if (updates.getStatus() != null) {
            license.setStatus(updates.getStatus());
        }

        return licenseRepository.save(license);
    }

    /**
     * Get license for a tenant
     */
    @Transactional(readOnly = true)
    public Optional<License> getLicenseByTenantId(Long tenantId) {
        return licenseRepository.findByTenantId(tenantId);
    }

    /**
     * Get all licenses (for super admin)
     */
    @Transactional(readOnly = true)
    public List<License> getAllLicenses() {
        return licenseRepository.findAll();
    }

    /**
     * Get active licenses
     */
    @Transactional(readOnly = true)
    public List<License> getActiveLicenses() {
        return licenseRepository.findByStatus(License.LicenseStatus.ACTIVE);
    }

    // ==================== TRANSACTION RECORDING ====================

    /**
     * Record a billable transaction (called when contribution, fine, loan, penalty is confirmed)
     */
    @Transactional
    public LicenseTransaction recordTransaction(Long tenantId, LicenseTransaction.TransactionType type,
                                                 BigDecimal amount, Long referenceId, String referenceType,
                                                 String description) {
        // Check for duplicates
        if (transactionRepository.existsByTenantIdAndReferenceIdAndReferenceType(tenantId, referenceId, referenceType)) {
            log.warn("Duplicate transaction detected: tenant={}, ref={}, type={}", tenantId, referenceId, referenceType);
            return null;
        }

        License license = licenseRepository.findByTenantId(tenantId).orElse(null);
        if (license == null) {
            log.warn("No license found for tenant {}, skipping transaction recording", tenantId);
            return null;
        }

        BigDecimal feePercent = getFeePercentForType(license, type);
        BigDecimal feeAmount = amount.multiply(feePercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        LicenseTransaction transaction = LicenseTransaction.builder()
                .tenant(tenant)
                .transactionType(type)
                .originalAmount(amount)
                .feePercent(feePercent)
                .feeAmount(feeAmount)
                .transactionDate(LocalDate.now())
                .referenceId(referenceId)
                .referenceType(referenceType)
                .description(description)
                .invoiced(false)
                .build();

        return transactionRepository.save(transaction);
    }

    /**
     * Get fee percentage for transaction type from license
     */
    private BigDecimal getFeePercentForType(License license, LicenseTransaction.TransactionType type) {
        return switch (type) {
            case CONTRIBUTION -> license.getContributionFeePercent() != null ? license.getContributionFeePercent() : BigDecimal.ZERO;
            case FINE -> license.getFineFeePercent() != null ? license.getFineFeePercent() : BigDecimal.ZERO;
            case LOAN_DISBURSEMENT, LOAN_INTEREST -> license.getLoanFeePercent() != null ? license.getLoanFeePercent() : BigDecimal.ZERO;
            case PENALTY -> license.getPenaltyFeePercent() != null ? license.getPenaltyFeePercent() : BigDecimal.ZERO;
            case MONTHLY_FIXED -> BigDecimal.valueOf(100); // Fixed fee is 100% of the amount
        };
    }

    /**
     * Get transactions for a tenant
     */
    @Transactional(readOnly = true)
    public List<LicenseTransaction> getTransactionsByTenantId(Long tenantId) {
        return transactionRepository.findByTenantIdOrderByTransactionDateDesc(tenantId);
    }

    /**
     * Get uninvoiced transactions for a tenant
     */
    @Transactional(readOnly = true)
    public List<LicenseTransaction> getUninvoicedTransactions(Long tenantId) {
        return transactionRepository.findByTenantIdAndInvoicedFalseOrderByTransactionDateAsc(tenantId);
    }

    /**
     * Get transactions for a specific invoice
     */
    @Transactional(readOnly = true)
    public List<LicenseTransaction> getTransactionsByInvoiceId(Long invoiceId) {
        return transactionRepository.findByInvoiceId(invoiceId);
    }

    // ==================== INVOICE GENERATION ====================

    /**
     * Generate invoice for a tenant for a specific billing period
     */
    @Transactional
    public Invoice generateInvoice(Long tenantId, LocalDate periodStart, LocalDate periodEnd) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        License license = licenseRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new RuntimeException("No license found for tenant"));

        // Check if invoice already exists for this period
        if (invoiceRepository.existsByTenantIdAndPeriod(tenantId, periodStart, periodEnd)) {
            throw new RuntimeException("Invoice already exists for this billing period");
        }

        // Get uninvoiced transactions in the period
        List<LicenseTransaction> transactions = transactionRepository
                .findUninvoicedByTenantIdAndDateRange(tenantId, periodStart, periodEnd);

        // Calculate fee totals by type
        BigDecimal contributionFees = BigDecimal.ZERO;
        BigDecimal fineFees = BigDecimal.ZERO;
        BigDecimal loanFees = BigDecimal.ZERO;
        BigDecimal penaltyFees = BigDecimal.ZERO;

        for (LicenseTransaction tx : transactions) {
            switch (tx.getTransactionType()) {
                case CONTRIBUTION -> contributionFees = contributionFees.add(tx.getFeeAmount());
                case FINE -> fineFees = fineFees.add(tx.getFeeAmount());
                case LOAN_DISBURSEMENT, LOAN_INTEREST -> loanFees = loanFees.add(tx.getFeeAmount());
                case PENALTY -> penaltyFees = penaltyFees.add(tx.getFeeAmount());
                default -> {}
            }
        }

        // Add monthly fixed fee
        BigDecimal fixedFee = license.getMonthlyFixedFee() != null ? license.getMonthlyFixedFee() : BigDecimal.ZERO;

        // Generate invoice number
        String invoiceNumber = generateInvoiceNumber(tenantId, periodStart);

        // Calculate due date (period end + grace period)
        int graceDays = license.getGracePeriodDays() != null ? license.getGracePeriodDays() : 7;
        LocalDate dueDate = periodEnd.plusDays(graceDays);

        // Create invoice
        Invoice invoice = Invoice.builder()
                .tenant(tenant)
                .invoiceNumber(invoiceNumber)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .issueDate(LocalDate.now())
                .dueDate(dueDate)
                .contributionFees(contributionFees)
                .fineFees(fineFees)
                .loanFees(loanFees)
                .penaltyFees(penaltyFees)
                .fixedFee(fixedFee)
                .adjustments(BigDecimal.ZERO)
                .status(Invoice.InvoiceStatus.SENT)  // Auto-send invoice
                .isAdhoc(false)
                .build();

        invoice.calculateSubtotal();
        invoice.calculateTotal();

        Invoice savedInvoice = invoiceRepository.save(invoice);

        // Link transactions to invoice (but keep invoiced=false until payment is confirmed)
        for (LicenseTransaction tx : transactions) {
            tx.setInvoice(savedInvoice);
            // Note: invoiced stays false until invoice is PAID
            transactionRepository.save(tx);
        }

        // Add fixed fee as a transaction if applicable
        if (fixedFee.compareTo(BigDecimal.ZERO) > 0) {
            LicenseTransaction fixedFeeTx = LicenseTransaction.builder()
                    .tenant(tenant)
                    .transactionType(LicenseTransaction.TransactionType.MONTHLY_FIXED)
                    .originalAmount(fixedFee)
                    .feePercent(BigDecimal.valueOf(100))
                    .feeAmount(fixedFee)
                    .transactionDate(periodEnd)
                    .description("Monthly fixed license fee")
                    .invoice(savedInvoice)
                    .invoiced(false) // Will be set to true when invoice is PAID
                    .build();
            transactionRepository.save(fixedFeeTx);
        }

        return savedInvoice;
    }

    /**
     * Generate ad-hoc invoice (mid-cycle or special billing)
     * This creates a flat-amount invoice NOT linked to transactions.
     * For ad-hoc invoice linked to actual transactions, use generateInvoiceFromUninvoicedTransactions
     */
    @Transactional
    public Invoice generateAdhocInvoice(Long tenantId, String description, BigDecimal amount) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        License license = licenseRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new RuntimeException("No license found for tenant"));

        // Validate: amount cannot exceed uninvoiced amount
        BigDecimal uninvoicedAmount = transactionRepository.sumUninvoicedFeesByTenantId(tenantId);
        if (amount.compareTo(uninvoicedAmount) > 0) {
            throw new RuntimeException("Invoice amount (KES " + amount + ") cannot exceed uninvoiced amount (KES " + uninvoicedAmount + ")");
        }

        LocalDate today = LocalDate.now();
        String invoiceNumber = generateInvoiceNumber(tenantId, today) + "-ADHOC";

        int graceDays = license.getGracePeriodDays() != null ? license.getGracePeriodDays() : 7;

        Invoice invoice = Invoice.builder()
                .tenant(tenant)
                .invoiceNumber(invoiceNumber)
                .periodStart(today)
                .periodEnd(today)
                .issueDate(today)
                .dueDate(today.plusDays(graceDays))
                .subtotal(amount)
                .totalAmount(amount)
                .balance(amount)
                .adjustments(BigDecimal.ZERO)
                .notes(description)
                .status(Invoice.InvoiceStatus.SENT)  // Auto-send invoice
                .isAdhoc(true)
                .build();

        Invoice savedInvoice = invoiceRepository.save(invoice);

        // Link uninvoiced transactions up to the invoice amount
        // This ensures the real-time amount decreases when this invoice is paid
        linkTransactionsToAdhocInvoice(tenantId, savedInvoice, amount);

        return savedInvoice;
    }

    /**
     * Link uninvoiced transactions to an ad-hoc invoice up to the specified amount.
     * Transactions are linked but not marked as invoiced until payment is confirmed.
     */
    private void linkTransactionsToAdhocInvoice(Long tenantId, Invoice invoice, BigDecimal targetAmount) {
        List<LicenseTransaction> uninvoicedTx = transactionRepository
                .findByTenantIdAndInvoicedFalseAndInvoiceIsNullOrderByTransactionDateAsc(tenantId);

        BigDecimal linkedAmount = BigDecimal.ZERO;
        int linkedCount = 0;

        for (LicenseTransaction tx : uninvoicedTx) {
            if (linkedAmount.compareTo(targetAmount) >= 0) {
                break; // We've linked enough
            }

            tx.setInvoice(invoice);
            transactionRepository.save(tx);
            linkedAmount = linkedAmount.add(tx.getFeeAmount());
            linkedCount++;
        }

        log.info("Linked {} transactions (total: KES {}) to ad-hoc invoice {}", 
                linkedCount, linkedAmount, invoice.getInvoiceNumber());
    }

    /**
     * Generate invoice number
     */
    private String generateInvoiceNumber(Long tenantId, LocalDate date) {
        long count = invoiceRepository.countByTenantIdAndYear(tenantId, date.getYear());
        return String.format("INV-%d-%04d-%03d", tenantId, date.getYear(), count + 1);
    }

    // ==================== INVOICE MANAGEMENT ====================

    /**
     * Send invoice (change status from DRAFT to SENT)
     */
    @Transactional
    public Invoice sendInvoice(Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        if (invoice.getStatus() != Invoice.InvoiceStatus.DRAFT) {
            throw new RuntimeException("Only draft invoices can be sent");
        }

        invoice.setStatus(Invoice.InvoiceStatus.SENT);
        return invoiceRepository.save(invoice);
    }

    /**
     * Record payment for invoice
     */
    @Transactional
    public Invoice recordPayment(Long invoiceId, BigDecimal amount, String paymentReference) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        BigDecimal newAmountPaid = invoice.getAmountPaid().add(amount);
        invoice.setAmountPaid(newAmountPaid);
        invoice.calculateBalance();

        if (paymentReference != null) {
            invoice.setPaymentReference(paymentReference);
        }

        // Update status based on payment
        if (invoice.getBalance().compareTo(BigDecimal.ZERO) <= 0) {
            invoice.setStatus(Invoice.InvoiceStatus.PAID);
            invoice.setPaidDate(LocalDate.now());
            invoice.setBalance(BigDecimal.ZERO);

            // Mark linked transactions as invoiced (now that payment is confirmed)
            markTransactionsAsInvoiced(invoiceId);

            // Reactivate license if it was suspended
            License license = licenseRepository.findByTenantId(invoice.getTenant().getId()).orElse(null);
            if (license != null && license.getStatus() == License.LicenseStatus.SUSPENDED) {
                // Check if all invoices are paid
                BigDecimal outstanding = invoiceRepository.sumOutstandingBalanceByTenantId(invoice.getTenant().getId());
                if (outstanding.compareTo(BigDecimal.ZERO) <= 0) {
                    license.setStatus(License.LicenseStatus.ACTIVE);
                    licenseRepository.save(license);
                }
            }
        } else {
            invoice.setStatus(Invoice.InvoiceStatus.PARTIAL);
        }

        return invoiceRepository.save(invoice);
    }

    /**
     * Mark all transactions linked to an invoice as invoiced
     * Called when an invoice is fully paid
     */
    private void markTransactionsAsInvoiced(Long invoiceId) {
        List<LicenseTransaction> transactions = transactionRepository.findByInvoiceId(invoiceId);
        for (LicenseTransaction tx : transactions) {
            tx.setInvoiced(true);
            transactionRepository.save(tx);
        }
        log.info("Marked {} transactions as invoiced for invoice {}", transactions.size(), invoiceId);
    }

    /**
     * Mark invoice as paid (full payment)
     */
    @Transactional
    public Invoice markInvoicePaid(Long invoiceId, String paymentReference) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        return recordPayment(invoiceId, invoice.getBalance(), paymentReference);
    }

    /**
     * Cancel invoice
     * Since transactions are not marked as invoiced until payment, canceling simply unlinks them
     */
    @Transactional
    public Invoice cancelInvoice(Long invoiceId, String reason) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        if (invoice.getStatus() == Invoice.InvoiceStatus.PAID) {
            throw new RuntimeException("Cannot cancel a paid invoice");
        }

        // Unlink transactions from invoice (they remain uninvoiced since payment wasn't confirmed)
        List<LicenseTransaction> transactions = transactionRepository.findByInvoiceId(invoiceId);
        for (LicenseTransaction tx : transactions) {
            if (tx.getTransactionType() != LicenseTransaction.TransactionType.MONTHLY_FIXED) {
                // Just remove the link, invoiced is already false
                tx.setInvoice(null);
                transactionRepository.save(tx);
            } else {
                // Delete the monthly fixed fee transaction since it was created for this invoice
                transactionRepository.delete(tx);
            }
        }

        invoice.setStatus(Invoice.InvoiceStatus.CANCELLED);
        invoice.setNotes((invoice.getNotes() != null ? invoice.getNotes() + "\n" : "") + "Cancelled: " + reason);
        return invoiceRepository.save(invoice);
    }

    /**
     * Chama admin confirms they have made a payment (awaiting super admin confirmation)
     */
    @Transactional
    public Invoice confirmPayment(Long invoiceId, Long tenantId, BigDecimal amount, String paymentReference) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        // Verify the invoice belongs to the tenant
        if (!invoice.getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("Invoice does not belong to your organization");
        }

        // Only SENT or OVERDUE invoices can have payment confirmed
        if (invoice.getStatus() != Invoice.InvoiceStatus.SENT && 
            invoice.getStatus() != Invoice.InvoiceStatus.OVERDUE &&
            invoice.getStatus() != Invoice.InvoiceStatus.PARTIAL) {
            throw new RuntimeException("Cannot confirm payment for invoice with status: " + invoice.getStatus());
        }

        invoice.setStatus(Invoice.InvoiceStatus.PENDING_CONFIRMATION);
        invoice.setClaimedAmount(amount);
        invoice.setPaymentReference(paymentReference);
        invoice.setPaymentConfirmedDate(LocalDate.now());

        return invoiceRepository.save(invoice);
    }

    /**
     * Super admin confirms receipt of payment
     */
    @Transactional
    public Invoice confirmReceipt(Long invoiceId, BigDecimal actualAmount, String notes) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        if (invoice.getStatus() != Invoice.InvoiceStatus.PENDING_CONFIRMATION) {
            throw new RuntimeException("Invoice is not pending confirmation");
        }

        // Use actual amount confirmed by super admin
        BigDecimal amountToRecord = actualAmount != null ? actualAmount : invoice.getClaimedAmount();
        
        if (amountToRecord == null || amountToRecord.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Invalid payment amount");
        }

        BigDecimal newAmountPaid = invoice.getAmountPaid().add(amountToRecord);
        invoice.setAmountPaid(newAmountPaid);
        invoice.calculateBalance();

        if (notes != null && !notes.isBlank()) {
            invoice.setNotes((invoice.getNotes() != null ? invoice.getNotes() + "\n" : "") + notes);
        }

        // Update status based on payment
        if (invoice.getBalance().compareTo(BigDecimal.ZERO) <= 0) {
            invoice.setStatus(Invoice.InvoiceStatus.PAID);
            invoice.setPaidDate(LocalDate.now());
            invoice.setBalance(BigDecimal.ZERO);

            // Mark linked transactions as invoiced (now that payment is confirmed)
            markTransactionsAsInvoiced(invoiceId);

            // Reactivate license if it was suspended
            License license = licenseRepository.findByTenantId(invoice.getTenant().getId()).orElse(null);
            if (license != null && license.getStatus() == License.LicenseStatus.SUSPENDED) {
                // Check if all invoices are paid
                BigDecimal outstanding = invoiceRepository.sumOutstandingBalanceByTenantId(invoice.getTenant().getId());
                if (outstanding.compareTo(BigDecimal.ZERO) <= 0) {
                    license.setStatus(License.LicenseStatus.ACTIVE);
                    licenseRepository.save(license);
                    log.info("Reactivated license for tenant {} after payment confirmation", invoice.getTenant().getId());
                }
            }
        } else {
            invoice.setStatus(Invoice.InvoiceStatus.PARTIAL);
        }

        return invoiceRepository.save(invoice);
    }

    /**
     * Super admin rejects payment confirmation (e.g., payment not received)
     */
    @Transactional
    public Invoice rejectPaymentConfirmation(Long invoiceId, String reason) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        if (invoice.getStatus() != Invoice.InvoiceStatus.PENDING_CONFIRMATION) {
            throw new RuntimeException("Invoice is not pending confirmation");
        }

        // Revert to previous status (SENT or OVERDUE based on due date)
        if (invoice.getDueDate().isBefore(LocalDate.now())) {
            invoice.setStatus(Invoice.InvoiceStatus.OVERDUE);
        } else {
            invoice.setStatus(Invoice.InvoiceStatus.SENT);
        }

        // Clear the confirmation data
        invoice.setClaimedAmount(null);
        invoice.setPaymentConfirmedDate(null);
        // Keep payment reference for audit trail

        if (reason != null && !reason.isBlank()) {
            invoice.setNotes((invoice.getNotes() != null ? invoice.getNotes() + "\n" : "") + 
                    "Payment confirmation rejected: " + reason);
        }

        return invoiceRepository.save(invoice);
    }

    /**
     * Get invoices for a tenant
     */
    @Transactional(readOnly = true)
    public List<Invoice> getInvoicesByTenantId(Long tenantId) {
        return invoiceRepository.findByTenantIdOrderByIssueDateDesc(tenantId);
    }

    /**
     * Get invoices for a tenant with pagination
     */
    @Transactional(readOnly = true)
    public Page<InvoiceDto> getInvoicesByTenantIdPaginated(Long tenantId, int page, int size, String status) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Invoice> invoicePage;
        
        if (status != null && !status.isEmpty()) {
            Invoice.InvoiceStatus invoiceStatus = Invoice.InvoiceStatus.valueOf(status);
            invoicePage = invoiceRepository.findByTenantIdAndStatus(tenantId, invoiceStatus, pageable);
        } else {
            invoicePage = invoiceRepository.findByTenantIdOrderByIssueDateDesc(tenantId, pageable);
        }
        
        return invoicePage.map(this::toInvoiceDto);
    }

    private InvoiceDto toInvoiceDto(Invoice invoice) {
        return InvoiceDto.builder()
                .id(invoice.getId())
                .tenantId(invoice.getTenant().getId())
                .tenantName(invoice.getTenant().getName())
                .invoiceNumber(invoice.getInvoiceNumber())
                .periodStart(invoice.getPeriodStart())
                .periodEnd(invoice.getPeriodEnd())
                .issueDate(invoice.getIssueDate())
                .dueDate(invoice.getDueDate())
                .contributionFees(invoice.getContributionFees())
                .fineFees(invoice.getFineFees())
                .loanFees(invoice.getLoanFees())
                .penaltyFees(invoice.getPenaltyFees())
                .fixedFee(invoice.getFixedFee())
                .subtotal(invoice.getSubtotal())
                .adjustments(invoice.getAdjustments())
                .adjustmentNotes(invoice.getAdjustmentNotes())
                .totalAmount(invoice.getTotalAmount())
                .amountPaid(invoice.getAmountPaid())
                .balance(invoice.getBalance())
                .status(invoice.getStatus().name())
                .paidDate(invoice.getPaidDate())
                .paymentReference(invoice.getPaymentReference())
                .paymentConfirmedDate(invoice.getPaymentConfirmedDate())
                .claimedAmount(invoice.getClaimedAmount())
                .notes(invoice.getNotes())
                .isAdhoc(invoice.getIsAdhoc())
                .build();
    }

    /**
     * Get invoice by ID
    /**
     * Get invoice by ID with tenant eagerly loaded
     */
    @Transactional(readOnly = true)
    public Optional<Invoice> getInvoiceById(Long invoiceId) {
        return invoiceRepository.findByIdWithTenant(invoiceId);
    }

    /**
     * Get all outstanding invoices (for super admin) - sent, partial, overdue
     */
    @Transactional(readOnly = true)
    public List<Invoice> getAllOutstandingInvoices() {
        return invoiceRepository.findAllOutstandingWithTenant();
    }

    /**
     * Get all pending invoices including drafts (for super admin)
     */
    @Transactional(readOnly = true)
    public List<Invoice> getAllPendingInvoices() {
        return invoiceRepository.findAllPendingWithTenant();
    }

    /**
     * Get all paid invoices
     */
    @Transactional(readOnly = true)
    public List<Invoice> getAllPaidInvoices() {
        return invoiceRepository.findAllPaidWithTenant();
    }

    /**
     * Get uninvoiced transactions for a specific month
     */
    @Transactional(readOnly = true)
    public List<LicenseTransaction> getUninvoicedTransactionsByMonth(int year, int month) {
        return transactionRepository.findUninvoicedByMonth(year, month);
    }

    /**
     * Get total uninvoiced amount for a tenant
     */
    @Transactional(readOnly = true)
    public BigDecimal getUninvoicedAmountByTenantId(Long tenantId) {
        return transactionRepository.sumUninvoicedFeesByTenantId(tenantId);
    }

    // ==================== SCHEDULED TASKS ====================

    /**
     * Check for overdue invoices and suspend licenses
     * Runs daily at midnight
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void checkAndSuspendOverdue() {
        log.info("Running overdue invoice check...");

        List<Invoice> overdueInvoices = invoiceRepository.findOverdueInvoices(LocalDate.now());

        for (Invoice invoice : overdueInvoices) {
            // Mark as overdue
            if (invoice.getStatus() != Invoice.InvoiceStatus.OVERDUE) {
                invoice.setStatus(Invoice.InvoiceStatus.OVERDUE);
                invoiceRepository.save(invoice);
            }

            // Suspend the license
            License license = licenseRepository.findByTenantId(invoice.getTenant().getId()).orElse(null);
            if (license != null && license.getStatus() == License.LicenseStatus.ACTIVE) {
                license.setStatus(License.LicenseStatus.SUSPENDED);
                licenseRepository.save(license);
                log.info("Suspended license for tenant {} due to overdue invoice {}", 
                        invoice.getTenant().getId(), invoice.getInvoiceNumber());
            }
        }

        log.info("Overdue check complete. Processed {} invoices.", overdueInvoices.size());
    }

    /**
     * Auto-generate invoices for all active licenses at billing day
     * Runs daily at 1 AM
     */
    @Scheduled(cron = "0 0 1 * * *")
    @Transactional
    public void autoGenerateInvoices() {
        log.info("Running auto invoice generation...");

        LocalDate today = LocalDate.now();
        int dayOfMonth = today.getDayOfMonth();

        // Get licenses where today is the billing day
        List<License> licensesToBill = licenseRepository.findByBillingDayOfMonthAndStatus(
                dayOfMonth, License.LicenseStatus.ACTIVE);

        for (License license : licensesToBill) {
            try {
                // Calculate billing period (previous month based on billing day)
                LocalDate periodEnd = today.minusDays(1);
                LocalDate periodStart = periodEnd.minusMonths(1).plusDays(1);

                // Check if invoice already exists
                if (!invoiceRepository.existsByTenantIdAndPeriod(license.getTenant().getId(), periodStart, periodEnd)) {
                    Invoice invoice = generateInvoice(license.getTenant().getId(), periodStart, periodEnd);
                    // Auto-send the invoice
                    sendInvoice(invoice.getId());
                    log.info("Generated and sent invoice {} for tenant {}", 
                            invoice.getInvoiceNumber(), license.getTenant().getId());
                }
            } catch (Exception e) {
                log.error("Failed to generate invoice for tenant {}: {}", 
                        license.getTenant().getId(), e.getMessage());
            }
        }

        log.info("Auto invoice generation complete.");
    }

    /**
     * Check if a tenant's license is active (for access control)
     */
    @Transactional(readOnly = true)
    public boolean isLicenseActive(Long tenantId) {
        return licenseRepository.findByTenantId(tenantId)
                .map(license -> license.getStatus() == License.LicenseStatus.ACTIVE 
                        || license.getStatus() == License.LicenseStatus.TRIAL)
                .orElse(true); // If no license, allow access (for initial setup)
    }

    // ==================== AD-HOC INVOICE WITH TRANSACTIONS ====================

    /**
     * Generate an ad-hoc invoice from all current uninvoiced transactions for a tenant.
     * Unlike generateAdhocInvoice (flat amount), this links to actual transactions
     * and will decrease the real-time amount when paid.
     */
    @Transactional
    public Invoice generateInvoiceFromUninvoicedTransactions(Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        License license = licenseRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new RuntimeException("No license found for tenant"));

        // Get all uninvoiced transactions (that are not already linked to a pending invoice)
        List<LicenseTransaction> transactions = transactionRepository
                .findByTenantIdAndInvoicedFalseAndInvoiceIsNullOrderByTransactionDateAsc(tenantId);

        if (transactions.isEmpty()) {
            throw new RuntimeException("No uninvoiced transactions found for this tenant");
        }

        LocalDate today = LocalDate.now();
        
        // Find date range from transactions
        LocalDate periodStart = transactions.stream()
                .map(LicenseTransaction::getTransactionDate)
                .min(LocalDate::compareTo)
                .orElse(today);
        LocalDate periodEnd = transactions.stream()
                .map(LicenseTransaction::getTransactionDate)
                .max(LocalDate::compareTo)
                .orElse(today);

        // Calculate fee totals by type
        BigDecimal contributionFees = BigDecimal.ZERO;
        BigDecimal fineFees = BigDecimal.ZERO;
        BigDecimal loanFees = BigDecimal.ZERO;
        BigDecimal penaltyFees = BigDecimal.ZERO;

        for (LicenseTransaction tx : transactions) {
            switch (tx.getTransactionType()) {
                case CONTRIBUTION -> contributionFees = contributionFees.add(tx.getFeeAmount());
                case FINE -> fineFees = fineFees.add(tx.getFeeAmount());
                case LOAN_DISBURSEMENT, LOAN_INTEREST -> loanFees = loanFees.add(tx.getFeeAmount());
                case PENALTY -> penaltyFees = penaltyFees.add(tx.getFeeAmount());
                default -> {}
            }
        }

        // Generate invoice number with ADHOC suffix
        String invoiceNumber = generateInvoiceNumber(tenantId, today) + "-ADHOC";

        int graceDays = license.getGracePeriodDays() != null ? license.getGracePeriodDays() : 7;
        LocalDate dueDate = today.plusDays(graceDays);

        Invoice invoice = Invoice.builder()
                .tenant(tenant)
                .invoiceNumber(invoiceNumber)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .issueDate(today)
                .dueDate(dueDate)
                .contributionFees(contributionFees)
                .fineFees(fineFees)
                .loanFees(loanFees)
                .penaltyFees(penaltyFees)
                .fixedFee(BigDecimal.ZERO) // No fixed fee for ad-hoc
                .adjustments(BigDecimal.ZERO)
                .status(Invoice.InvoiceStatus.SENT)
                .isAdhoc(true)
                .notes("Ad-hoc invoice generated from current uninvoiced transactions")
                .build();

        invoice.calculateSubtotal();
        invoice.calculateTotal();

        Invoice savedInvoice = invoiceRepository.save(invoice);

        // Link transactions to invoice (but keep invoiced=false until paid)
        for (LicenseTransaction tx : transactions) {
            tx.setInvoice(savedInvoice);
            transactionRepository.save(tx);
        }

        log.info("Generated ad-hoc invoice {} with {} transactions for tenant {}", 
                invoiceNumber, transactions.size(), tenantId);

        return savedInvoice;
    }

    // ==================== MONTH-END AUTO GENERATION ====================

    /**
     * Auto-generate invoices at month-end for all active tenants.
     * Runs on the last day of each month at 11 PM.
     * This captures all uninvoiced transactions for the current month.
     */
    @Scheduled(cron = "0 0 23 L * *") // Last day of month at 11 PM
    @Transactional
    public void autoGenerateMonthEndInvoices() {
        log.info("Running month-end invoice generation...");

        LocalDate today = LocalDate.now();
        YearMonth currentMonth = YearMonth.from(today);
        LocalDate periodStart = currentMonth.atDay(1);
        LocalDate periodEnd = currentMonth.atEndOfMonth();

        // Get all active and trial licenses
        List<License> activeLicenses = licenseRepository.findByStatusIn(
                List.of(License.LicenseStatus.ACTIVE, License.LicenseStatus.TRIAL));

        int generated = 0;
        int skipped = 0;

        for (License license : activeLicenses) {
            try {
                Long tenantId = license.getTenant().getId();

                // Check if monthly invoice already exists for this period
                if (invoiceRepository.existsByTenantIdAndPeriod(tenantId, periodStart, periodEnd)) {
                    log.debug("Monthly invoice already exists for tenant {} period {}-{}", 
                            tenantId, periodStart, periodEnd);
                    skipped++;
                    continue;
                }

                // Check if there are any uninvoiced transactions
                List<LicenseTransaction> uninvoicedTx = transactionRepository
                        .findByTenantIdAndInvoicedFalseAndInvoiceIsNullOrderByTransactionDateAsc(tenantId);

                if (uninvoicedTx.isEmpty()) {
                    log.debug("No uninvoiced transactions for tenant {}, skipping", tenantId);
                    skipped++;
                    continue;
                }

                // Generate the month-end invoice
                Invoice invoice = generateInvoice(tenantId, periodStart, periodEnd);
                log.info("Generated month-end invoice {} for tenant {} with {} transactions", 
                        invoice.getInvoiceNumber(), tenantId, uninvoicedTx.size());
                generated++;

            } catch (Exception e) {
                log.error("Failed to generate month-end invoice for tenant {}: {}", 
                        license.getTenant().getId(), e.getMessage());
            }
        }

        log.info("Month-end invoice generation complete. Generated: {}, Skipped: {}", generated, skipped);
    }
}
