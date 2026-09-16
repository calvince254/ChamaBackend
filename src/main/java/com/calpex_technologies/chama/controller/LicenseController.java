package com.calpex_technologies.chama.controller;

import com.calpex_technologies.chama.dto.*;
import com.calpex_technologies.chama.entity.Invoice;
import com.calpex_technologies.chama.entity.License;
import com.calpex_technologies.chama.entity.LicenseTransaction;
import com.calpex_technologies.chama.security.UserPrincipal;
import com.calpex_technologies.chama.service.InvoicePdfService;
import com.calpex_technologies.chama.service.LicenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * License management endpoints for super admin.
 * Handles license CRUD, invoice generation, and payment tracking.
 */
@RestController
@RequestMapping("/api/licenses")
@RequiredArgsConstructor
public class LicenseController {

    private final LicenseService licenseService;
    private final InvoicePdfService invoicePdfService;

    // ==================== LICENSE MANAGEMENT ====================

    /**
     * Get all licenses (super admin only)
     */
    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<LicenseDto>> getAllLicenses() {
        List<License> licenses = licenseService.getAllLicenses();
        return ResponseEntity.ok(licenses.stream().map(this::toDto).collect(Collectors.toList()));
    }

    /**
     * Get license by tenant ID
     */
    @GetMapping("/tenant/{tenantId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<LicenseDto> getLicenseByTenantId(@PathVariable Long tenantId) {
        return licenseService.getLicenseByTenantId(tenantId)
                .map(this::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create a new license for a tenant
     */
    @PostMapping("/tenant/{tenantId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<LicenseDto> createLicense(@PathVariable Long tenantId, @RequestBody LicenseDto request) {
        License license = License.builder()
                .contributionFeePercent(request.getContributionFeePercent())
                .fineFeePercent(request.getFineFeePercent())
                .loanFeePercent(request.getLoanFeePercent())
                .penaltyFeePercent(request.getPenaltyFeePercent())
                .monthlyFixedFee(request.getMonthlyFixedFee())
                .gracePeriodDays(request.getGracePeriodDays())
                .billingDayOfMonth(request.getBillingDayOfMonth())
                .build();

        License created = licenseService.createLicense(tenantId, license);
        return ResponseEntity.ok(toDto(created));
    }

    /**
     * Update license settings
     */
    @PutMapping("/{licenseId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<LicenseDto> updateLicense(@PathVariable Long licenseId, @RequestBody LicenseDto request) {
        License updates = License.builder()
                .contributionFeePercent(request.getContributionFeePercent())
                .fineFeePercent(request.getFineFeePercent())
                .loanFeePercent(request.getLoanFeePercent())
                .penaltyFeePercent(request.getPenaltyFeePercent())
                .monthlyFixedFee(request.getMonthlyFixedFee())
                .gracePeriodDays(request.getGracePeriodDays())
                .billingDayOfMonth(request.getBillingDayOfMonth())
                .status(request.getStatus() != null ? License.LicenseStatus.valueOf(request.getStatus()) : null)
                .build();

        License updated = licenseService.updateLicense(licenseId, updates);
        return ResponseEntity.ok(toDto(updated));
    }

    /**
     * Activate a license
     */
    @PostMapping("/{licenseId}/activate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<LicenseDto> activateLicense(@PathVariable Long licenseId) {
        License updates = License.builder().status(License.LicenseStatus.ACTIVE).build();
        License updated = licenseService.updateLicense(licenseId, updates);
        return ResponseEntity.ok(toDto(updated));
    }

    /**
     * Suspend a license
     */
    @PostMapping("/{licenseId}/suspend")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<LicenseDto> suspendLicense(@PathVariable Long licenseId) {
        License updates = License.builder().status(License.LicenseStatus.SUSPENDED).build();
        License updated = licenseService.updateLicense(licenseId, updates);
        return ResponseEntity.ok(toDto(updated));
    }

    // ==================== TRANSACTION VIEWING ====================

    /**
     * Get transactions for a tenant
     */
    @GetMapping("/transactions/tenant/{tenantId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<LicenseTransactionDto>> getTransactionsByTenant(@PathVariable Long tenantId) {
        List<LicenseTransaction> transactions = licenseService.getTransactionsByTenantId(tenantId);
        return ResponseEntity.ok(transactions.stream().map(this::toDto).collect(Collectors.toList()));
    }

    /**
     * Get uninvoiced transactions for a tenant
     */
    @GetMapping("/transactions/tenant/{tenantId}/uninvoiced")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<LicenseTransactionDto>> getUninvoicedTransactions(@PathVariable Long tenantId) {
        List<LicenseTransaction> transactions = licenseService.getUninvoicedTransactions(tenantId);
        return ResponseEntity.ok(transactions.stream().map(this::toDto).collect(Collectors.toList()));
    }

    // ==================== INVOICE MANAGEMENT ====================

    /**
     * Get all outstanding invoices (super admin) - includes drafts, sent, partial, overdue
     */
    @GetMapping("/invoices/outstanding")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<InvoiceDto>> getOutstandingInvoices() {
        List<Invoice> invoices = licenseService.getAllPendingInvoices();
        return ResponseEntity.ok(invoices.stream().map(this::toDto).collect(Collectors.toList()));
    }

    /**
     * Get all paid invoices (for super admin)
     */
    @GetMapping("/invoices/paid")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<InvoiceDto>> getPaidInvoices() {
        List<Invoice> invoices = licenseService.getAllPaidInvoices();
        return ResponseEntity.ok(invoices.stream().map(this::toDto).collect(Collectors.toList()));
    }

    /**
     * Get billable (uninvoiced) transactions for a month
     */
    @GetMapping("/transactions/billable")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<LicenseTransactionDto>> getBillableTransactions(
            @RequestParam int year,
            @RequestParam int month) {
        List<LicenseTransaction> transactions = licenseService.getUninvoicedTransactionsByMonth(year, month);
        return ResponseEntity.ok(transactions.stream().map(this::toDto).collect(Collectors.toList()));
    }

    /**
     * Get invoices for a tenant
     */
    @GetMapping("/invoices/tenant/{tenantId}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or @securityService.isTenantMember(#tenantId)")
    @Transactional(readOnly = true)
    public ResponseEntity<List<InvoiceDto>> getInvoicesByTenant(@PathVariable Long tenantId) {
        List<Invoice> invoices = licenseService.getInvoicesByTenantId(tenantId);
        return ResponseEntity.ok(invoices.stream().map(this::toDto).collect(Collectors.toList()));
    }

    /**
     * Get invoice by ID
     */
    @GetMapping("/invoices/{invoiceId}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or @securityService.canAccessInvoice(#invoiceId)")
    @Transactional(readOnly = true)
    public ResponseEntity<InvoiceDto> getInvoiceById(@PathVariable Long invoiceId) {
        return licenseService.getInvoiceById(invoiceId)
                .map(this::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Generate invoice for a tenant
     */
    @PostMapping("/invoices/generate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<InvoiceDto> generateInvoice(@RequestBody GenerateInvoiceRequest request) {
        Invoice invoice;
        if (Boolean.TRUE.equals(request.getIsAdhoc())) {
            if (request.getAmount() != null && request.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                // Flat amount ad-hoc invoice (not linked to transactions)
                invoice = licenseService.generateAdhocInvoice(
                        request.getTenantId(), 
                        request.getDescription(), 
                        request.getAmount());
            } else {
                // Ad-hoc invoice from all uninvoiced transactions
                invoice = licenseService.generateInvoiceFromUninvoicedTransactions(request.getTenantId());
            }
        } else {
            invoice = licenseService.generateInvoice(
                    request.getTenantId(), 
                    request.getPeriodStart(), 
                    request.getPeriodEnd());
        }
        return ResponseEntity.ok(toDto(invoice));
    }

    /**
     * Send invoice (change from DRAFT to SENT)
     */
    @PostMapping("/invoices/{invoiceId}/send")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<InvoiceDto> sendInvoice(@PathVariable Long invoiceId) {
        Invoice invoice = licenseService.sendInvoice(invoiceId);
        return ResponseEntity.ok(toDto(invoice));
    }

    /**
     * Record payment for an invoice
     */
    @PostMapping("/invoices/{invoiceId}/payment")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<InvoiceDto> recordPayment(
            @PathVariable Long invoiceId, 
            @RequestBody RecordPaymentRequest request) {
        Invoice invoice = licenseService.recordPayment(
                invoiceId, 
                request.getAmount(), 
                request.getPaymentReference());
        return ResponseEntity.ok(toDto(invoice));
    }

    /**
     * Mark invoice as fully paid
     */
    @PostMapping("/invoices/{invoiceId}/mark-paid")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<InvoiceDto> markInvoicePaid(
            @PathVariable Long invoiceId,
            @RequestBody(required = false) RecordPaymentRequest request) {
        String paymentRef = request != null ? request.getPaymentReference() : null;
        Invoice invoice = licenseService.markInvoicePaid(invoiceId, paymentRef);
        return ResponseEntity.ok(toDto(invoice));
    }

    /**
     * Cancel an invoice
     */
    @PostMapping("/invoices/{invoiceId}/cancel")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<InvoiceDto> cancelInvoice(
            @PathVariable Long invoiceId,
            @RequestBody(required = false) String reason) {
        Invoice invoice = licenseService.cancelInvoice(invoiceId, reason != null ? reason : "Cancelled by admin");
        return ResponseEntity.ok(toDto(invoice));
    }

    /**
     * Manually trigger overdue check (for testing or manual intervention)
     */
    @PostMapping("/check-overdue")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<String> checkOverdue() {
        licenseService.checkAndSuspendOverdue();
        return ResponseEntity.ok("Overdue check completed");
    }

    // ==================== CHAMA ADMIN ENDPOINTS ====================

    /**
     * Get invoices for the current user's tenant (chama admin view)
     */
    @GetMapping("/invoices/my")
    @Transactional(readOnly = true)
    public ResponseEntity<List<InvoiceDto>> getMyInvoices(@AuthenticationPrincipal UserPrincipal principal) {
        Long tenantId = principal.getTenantId();
        List<Invoice> invoices = licenseService.getInvoicesByTenantId(tenantId);
        return ResponseEntity.ok(invoices.stream().map(this::toDto).collect(Collectors.toList()));
    }

    /**
     * Get invoices for the current user's tenant with pagination
     */
    @GetMapping("/invoices/my/paginated")
    @Transactional(readOnly = true)
    public ResponseEntity<PageResponse<InvoiceDto>> getMyInvoicesPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long tenantId = principal.getTenantId();
        Page<InvoiceDto> result = licenseService.getInvoicesByTenantIdPaginated(tenantId, page, size, status);
        return ResponseEntity.ok(PageResponse.from(result));
    }

    /**
     * Get a specific invoice for the current user's tenant
     */
    @GetMapping("/invoices/my/{invoiceId}")
    @Transactional(readOnly = true)
    public ResponseEntity<InvoiceDto> getMyInvoice(
            @PathVariable Long invoiceId,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long tenantId = principal.getTenantId();
        return licenseService.getInvoiceById(invoiceId)
                .filter(inv -> inv.getTenant().getId().equals(tenantId))
                .map(this::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get license status for current tenant (for displaying in dashboard)
     */
    @GetMapping("/my-license")
    @Transactional(readOnly = true)
    public ResponseEntity<LicenseDto> getMyLicense(@AuthenticationPrincipal UserPrincipal principal) {
        Long tenantId = principal.getTenantId();
        return licenseService.getLicenseByTenantId(tenantId)
                .map(this::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Download invoice as PDF (chama admin)
     */
    @GetMapping("/invoices/my/{invoiceId}/pdf")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> downloadMyInvoicePdf(
            @PathVariable Long invoiceId,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long tenantId = principal.getTenantId();
        
        return licenseService.getInvoiceById(invoiceId)
                .filter(inv -> inv.getTenant().getId().equals(tenantId))
                .map(invoice -> {
                    List<LicenseTransaction> transactions = licenseService.getTransactionsByInvoiceId(invoiceId);
                    byte[] pdfBytes = invoicePdfService.generateInvoicePdf(invoice, transactions);
                    
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_PDF);
                    headers.setContentDispositionFormData("attachment", 
                            "invoice-" + invoice.getInvoiceNumber() + ".pdf");
                    headers.setContentLength(pdfBytes.length);
                    
                    return ResponseEntity.ok().headers(headers).body(pdfBytes);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Download invoice as PDF (super admin)
     */
    @GetMapping("/invoices/{invoiceId}/pdf")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> downloadInvoicePdf(@PathVariable Long invoiceId) {
        return licenseService.getInvoiceById(invoiceId)
                .map(invoice -> {
                    List<LicenseTransaction> transactions = licenseService.getTransactionsByInvoiceId(invoiceId);
                    byte[] pdfBytes = invoicePdfService.generateInvoicePdf(invoice, transactions);
                    
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_PDF);
                    headers.setContentDispositionFormData("attachment", 
                            "invoice-" + invoice.getInvoiceNumber() + ".pdf");
                    headers.setContentLength(pdfBytes.length);
                    
                    return ResponseEntity.ok().headers(headers).body(pdfBytes);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ==================== PAYMENT CONFIRMATION FLOW ====================

    /**
     * Chama admin confirms they have made a payment
     */
    @PostMapping("/invoices/my/{invoiceId}/confirm-payment")
    public ResponseEntity<InvoiceDto> confirmPayment(
            @PathVariable Long invoiceId,
            @RequestBody ConfirmPaymentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long tenantId = principal.getTenantId();
        Invoice invoice = licenseService.confirmPayment(
                invoiceId, 
                tenantId, 
                request.getAmount(), 
                request.getPaymentReference());
        return ResponseEntity.ok(toDto(invoice));
    }

    /**
     * Super admin confirms receipt of payment
     */
    @PostMapping("/invoices/{invoiceId}/confirm-receipt")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<InvoiceDto> confirmReceipt(
            @PathVariable Long invoiceId,
            @RequestBody(required = false) RecordPaymentRequest request) {
        Invoice invoice = licenseService.confirmReceipt(
                invoiceId,
                request != null ? request.getAmount() : null,
                request != null ? request.getPaymentReference() : null);
        return ResponseEntity.ok(toDto(invoice));
    }

    /**
     * Super admin rejects payment confirmation
     */
    @PostMapping("/invoices/{invoiceId}/reject-payment")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<InvoiceDto> rejectPaymentConfirmation(
            @PathVariable Long invoiceId,
            @RequestBody(required = false) String reason) {
        Invoice invoice = licenseService.rejectPaymentConfirmation(
                invoiceId,
                reason != null ? reason : "Payment not verified");
        return ResponseEntity.ok(toDto(invoice));
    }

    // ==================== DTO CONVERTERS ====================

    private LicenseDto toDto(License license) {
        BigDecimal uninvoicedAmount = licenseService.getUninvoicedAmountByTenantId(license.getTenant().getId());
        return LicenseDto.builder()
                .id(license.getId())
                .tenantId(license.getTenant().getId())
                .tenantName(license.getTenant().getName())
                .contributionFeePercent(license.getContributionFeePercent())
                .fineFeePercent(license.getFineFeePercent())
                .loanFeePercent(license.getLoanFeePercent())
                .penaltyFeePercent(license.getPenaltyFeePercent())
                .monthlyFixedFee(license.getMonthlyFixedFee())
                .gracePeriodDays(license.getGracePeriodDays())
                .billingDayOfMonth(license.getBillingDayOfMonth())
                .status(license.getStatus().name())
                .uninvoicedAmount(uninvoicedAmount)
                .build();
    }

    private InvoiceDto toDto(Invoice invoice) {
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

    private LicenseTransactionDto toDto(LicenseTransaction tx) {
        return LicenseTransactionDto.builder()
                .id(tx.getId())
                .tenantId(tx.getTenant().getId())
                .tenantName(tx.getTenant().getName())
                .transactionType(tx.getTransactionType().name())
                .originalAmount(tx.getOriginalAmount())
                .feePercent(tx.getFeePercent())
                .feeAmount(tx.getFeeAmount())
                .transactionDate(tx.getTransactionDate())
                .referenceId(tx.getReferenceId())
                .referenceType(tx.getReferenceType())
                .description(tx.getDescription())
                .invoiceId(tx.getInvoice() != null ? tx.getInvoice().getId() : null)
                .invoiceNumber(tx.getInvoice() != null ? tx.getInvoice().getInvoiceNumber() : null)
                .invoiced(tx.getInvoiced())
                .build();
    }
}
