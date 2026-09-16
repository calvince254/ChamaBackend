package com.calpex_technologies.chama.service;

import com.calpex_technologies.chama.entity.Invoice;
import com.calpex_technologies.chama.entity.LicenseTransaction;
import com.calpex_technologies.chama.entity.SystemSettings;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Service for generating PDF invoices.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvoicePdfService {

    private final SystemSettingsService systemSettingsService;

    private static final DeviceRgb PRIMARY_COLOR = new DeviceRgb(11, 79, 71); // Teal to match app theme
    private static final DeviceRgb LIGHT_GRAY = new DeviceRgb(243, 244, 246);
    private static final DeviceRgb DARK_GRAY = new DeviceRgb(75, 85, 99);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(new Locale("en", "KE"));

    static {
        CURRENCY_FORMAT.setMaximumFractionDigits(2);
        CURRENCY_FORMAT.setMinimumFractionDigits(2);
    }

    /**
     * Generate a PDF invoice document.
     */
    public byte[] generateInvoicePdf(Invoice invoice, List<LicenseTransaction> transactions) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf, PageSize.A4);
            document.setMargins(40, 40, 40, 40);

            PdfFont regularFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            
            // Get system settings for platform info (read-only safe)
            SystemSettings settings = systemSettingsService.getSettingsReadOnly();

            // Header Section
            addHeader(document, invoice, boldFont, regularFont, settings);
            
            // Invoice Details Section
            addInvoiceDetails(document, invoice, boldFont, regularFont);
            
            // Fee Summary Section
            addFeeSummary(document, invoice, boldFont, regularFont);
            
            // Transaction Details Section (if available)
            if (transactions != null && !transactions.isEmpty()) {
                addTransactionDetails(document, transactions, boldFont, regularFont);
            }
            
            // Totals Section
            addTotals(document, invoice, boldFont, regularFont);
            
            // Payment Information Section
            addPaymentInfo(document, invoice, boldFont, regularFont, settings);
            
            // Footer
            addFooter(document, regularFont, settings);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Error generating invoice PDF for invoice {}: {}", invoice.getInvoiceNumber(), e.getMessage(), e);
            throw new RuntimeException("Failed to generate invoice PDF", e);
        }
    }

    private void addHeader(Document document, Invoice invoice, PdfFont boldFont, PdfFont regularFont, SystemSettings settings) {
        // Company Name / Platform Name from settings
        String platformName = settings.getPlatformName() != null ? settings.getPlatformName() : "Calpex Chama Manager";
        
        Paragraph header = new Paragraph(platformName)
                .setFont(boldFont)
                .setFontSize(16)
                .setFontColor(PRIMARY_COLOR)
                .setTextAlignment(TextAlignment.LEFT);
        document.add(header);

        // Invoice Title
        Paragraph title = new Paragraph("INVOICE")
                .setFont(boldFont)
                .setFontSize(28)
                .setFontColor(PRIMARY_COLOR)
                .setTextAlignment(TextAlignment.RIGHT)
                .setMarginTop(-25);
        document.add(title);

        // Separator line
        document.add(new Paragraph("")
                .setBorderBottom(new SolidBorder(PRIMARY_COLOR, 2))
                .setMarginBottom(20));
    }

    private void addInvoiceDetails(Document document, Invoice invoice, PdfFont boldFont, PdfFont regularFont) {
        Table detailsTable = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(20);

        // Left side - Bill To
        Cell billToCell = new Cell()
                .setBorder(Border.NO_BORDER)
                .setPadding(10);
        
        billToCell.add(new Paragraph("BILL TO")
                .setFont(boldFont)
                .setFontSize(10)
                .setFontColor(DARK_GRAY));
        billToCell.add(new Paragraph(invoice.getTenant().getName())
                .setFont(boldFont)
                .setFontSize(14)
                .setMarginTop(5));
        
        if (invoice.getTenant().getContactEmail() != null) {
            billToCell.add(new Paragraph(invoice.getTenant().getContactEmail())
                    .setFont(regularFont)
                    .setFontSize(10)
                    .setFontColor(DARK_GRAY));
        }
        if (invoice.getTenant().getContactPhone() != null) {
            billToCell.add(new Paragraph(invoice.getTenant().getContactPhone())
                    .setFont(regularFont)
                    .setFontSize(10)
                    .setFontColor(DARK_GRAY));
        }
        
        detailsTable.addCell(billToCell);

        // Right side - Invoice Info
        Cell infoCell = new Cell()
                .setBorder(Border.NO_BORDER)
                .setPadding(10)
                .setTextAlignment(TextAlignment.RIGHT);
        
        infoCell.add(createInfoLine("Invoice Number:", invoice.getInvoiceNumber(), boldFont, regularFont));
        infoCell.add(createInfoLine("Issue Date:", formatDate(invoice.getIssueDate()), boldFont, regularFont));
        infoCell.add(createInfoLine("Due Date:", formatDate(invoice.getDueDate()), boldFont, regularFont));
        infoCell.add(createInfoLine("Billing Period:", 
                formatDate(invoice.getPeriodStart()) + " - " + formatDate(invoice.getPeriodEnd()), 
                boldFont, regularFont));
        
        // Status badge
        Paragraph statusPara = new Paragraph(invoice.getStatus().name())
                .setFont(boldFont)
                .setFontSize(10)
                .setPadding(5)
                .setMarginTop(10)
                .setBackgroundColor(getStatusColor(invoice.getStatus()))
                .setFontColor(ColorConstants.WHITE)
                .setTextAlignment(TextAlignment.CENTER);
        infoCell.add(statusPara);
        
        detailsTable.addCell(infoCell);
        document.add(detailsTable);
    }

    private Paragraph createInfoLine(String label, String value, PdfFont boldFont, PdfFont regularFont) {
        return new Paragraph()
                .add(new Text(label + " ").setFont(regularFont).setFontSize(9).setFontColor(DARK_GRAY))
                .add(new Text(value).setFont(boldFont).setFontSize(10))
                .setMarginBottom(3);
    }

    private void addFeeSummary(Document document, Invoice invoice, PdfFont boldFont, PdfFont regularFont) {
        document.add(new Paragraph("FEE SUMMARY")
                .setFont(boldFont)
                .setFontSize(12)
                .setFontColor(PRIMARY_COLOR)
                .setMarginTop(10)
                .setMarginBottom(10));

        Table feeTable = new Table(UnitValue.createPercentArray(new float[]{3, 1}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(20);

        // Header row
        feeTable.addHeaderCell(createHeaderCell("Description", boldFont));
        feeTable.addHeaderCell(createHeaderCell("Amount", boldFont).setTextAlignment(TextAlignment.RIGHT));

        // Fee rows
        addFeeRow(feeTable, "Contribution Fees", invoice.getContributionFees(), regularFont);
        addFeeRow(feeTable, "Fine Fees", invoice.getFineFees(), regularFont);
        addFeeRow(feeTable, "Loan Fees", invoice.getLoanFees(), regularFont);
        addFeeRow(feeTable, "Penalty Fees", invoice.getPenaltyFees(), regularFont);
        addFeeRow(feeTable, "Fixed Monthly Fee", invoice.getFixedFee(), regularFont);

        document.add(feeTable);
    }

    private void addFeeRow(Table table, String description, BigDecimal amount, PdfFont font) {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            table.addCell(new Cell()
                    .add(new Paragraph(description).setFont(font).setFontSize(10))
                    .setBorder(Border.NO_BORDER)
                    .setBorderBottom(new SolidBorder(LIGHT_GRAY, 1))
                    .setPadding(8));
            table.addCell(new Cell()
                    .add(new Paragraph(formatCurrency(amount)).setFont(font).setFontSize(10))
                    .setBorder(Border.NO_BORDER)
                    .setBorderBottom(new SolidBorder(LIGHT_GRAY, 1))
                    .setPadding(8)
                    .setTextAlignment(TextAlignment.RIGHT));
        }
    }

    private void addTransactionDetails(Document document, List<LicenseTransaction> transactions, 
                                        PdfFont boldFont, PdfFont regularFont) {
        document.add(new Paragraph("TRANSACTION DETAILS")
                .setFont(boldFont)
                .setFontSize(12)
                .setFontColor(PRIMARY_COLOR)
                .setMarginTop(10)
                .setMarginBottom(10));

        Table txTable = new Table(UnitValue.createPercentArray(new float[]{2, 3, 1.5f, 1, 1.5f}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(20)
                .setFontSize(8);

        // Header row
        txTable.addHeaderCell(createHeaderCell("Date", boldFont));
        txTable.addHeaderCell(createHeaderCell("Description", boldFont));
        txTable.addHeaderCell(createHeaderCell("Amount", boldFont).setTextAlignment(TextAlignment.RIGHT));
        txTable.addHeaderCell(createHeaderCell("Fee %", boldFont).setTextAlignment(TextAlignment.RIGHT));
        txTable.addHeaderCell(createHeaderCell("Fee", boldFont).setTextAlignment(TextAlignment.RIGHT));

        // Transaction rows (limit to first 20 for space)
        int count = 0;
        for (LicenseTransaction tx : transactions) {
            if (count >= 20) {
                // Add "and X more..." row
                int remaining = transactions.size() - 20;
                txTable.addCell(new Cell(1, 5)
                        .add(new Paragraph("... and " + remaining + " more transactions")
                                .setFont(regularFont).setFontSize(8).setFontColor(DARK_GRAY))
                        .setBorder(Border.NO_BORDER)
                        .setPadding(5));
                break;
            }
            
            addTransactionRow(txTable, tx, regularFont);
            count++;
        }

        document.add(txTable);
    }

    private void addTransactionRow(Table table, LicenseTransaction tx, PdfFont font) {
        table.addCell(createDataCell(formatDate(tx.getTransactionDate()), font));
        table.addCell(createDataCell(tx.getDescription() != null ? 
                truncate(tx.getDescription(), 40) : tx.getTransactionType().name(), font));
        table.addCell(createDataCell(formatCurrency(tx.getOriginalAmount()), font)
                .setTextAlignment(TextAlignment.RIGHT));
        table.addCell(createDataCell(tx.getFeePercent() + "%", font)
                .setTextAlignment(TextAlignment.RIGHT));
        table.addCell(createDataCell(formatCurrency(tx.getFeeAmount()), font)
                .setTextAlignment(TextAlignment.RIGHT));
    }

    private void addTotals(Document document, Invoice invoice, PdfFont boldFont, PdfFont regularFont) {
        Table totalsTable = new Table(UnitValue.createPercentArray(new float[]{3, 1}))
                .setWidth(UnitValue.createPercentValue(50))
                .setHorizontalAlignment(HorizontalAlignment.RIGHT)
                .setMarginBottom(20);

        // Subtotal
        addTotalRow(totalsTable, "Subtotal", invoice.getSubtotal(), regularFont, false);
        
        // Adjustments (if any)
        if (invoice.getAdjustments() != null && invoice.getAdjustments().compareTo(BigDecimal.ZERO) != 0) {
            String adjLabel = invoice.getAdjustments().compareTo(BigDecimal.ZERO) > 0 ? 
                    "Additional Charges" : "Discount/Credit";
            addTotalRow(totalsTable, adjLabel, invoice.getAdjustments().abs(), regularFont, false);
        }
        
        // Total
        addTotalRow(totalsTable, "Total Amount", invoice.getTotalAmount(), boldFont, true);
        
        // Amount Paid
        if (invoice.getAmountPaid() != null && invoice.getAmountPaid().compareTo(BigDecimal.ZERO) > 0) {
            addTotalRow(totalsTable, "Amount Paid", invoice.getAmountPaid().negate(), regularFont, false);
        }
        
        // Balance Due
        addTotalRow(totalsTable, "Balance Due", invoice.getBalance(), boldFont, true);

        document.add(totalsTable);
    }

    private void addTotalRow(Table table, String label, BigDecimal amount, PdfFont font, boolean highlight) {
        DeviceRgb bgColor = highlight ? LIGHT_GRAY : null;
        
        Cell labelCell = new Cell()
                .add(new Paragraph(label).setFont(font).setFontSize(10))
                .setBorder(Border.NO_BORDER)
                .setPadding(8);
        if (bgColor != null) labelCell.setBackgroundColor(bgColor);
        
        Cell amountCell = new Cell()
                .add(new Paragraph(formatCurrency(amount)).setFont(font).setFontSize(10))
                .setBorder(Border.NO_BORDER)
                .setPadding(8)
                .setTextAlignment(TextAlignment.RIGHT);
        if (bgColor != null) amountCell.setBackgroundColor(bgColor);
        
        table.addCell(labelCell);
        table.addCell(amountCell);
    }

    private void addPaymentInfo(Document document, Invoice invoice, PdfFont boldFont, PdfFont regularFont, SystemSettings settings) {
        document.add(new Paragraph("PAYMENT INFORMATION")
                .setFont(boldFont)
                .setFontSize(12)
                .setFontColor(PRIMARY_COLOR)
                .setMarginTop(20)
                .setMarginBottom(10));

        Table paymentTable = new Table(UnitValue.createPercentArray(new float[]{1}))
                .setWidth(UnitValue.createPercentValue(100))
                .setBackgroundColor(LIGHT_GRAY)
                .setMarginBottom(20);

        Cell paymentCell = new Cell()
                .setBorder(Border.NO_BORDER)
                .setPadding(15);

        paymentCell.add(new Paragraph("Please make payment to:")
                .setFont(regularFont)
                .setFontSize(10)
                .setMarginBottom(5));
        
        // M-PESA payment info from settings
        String mpesaPaybill = settings.getMpesaPaybill();
        if (mpesaPaybill != null && !mpesaPaybill.isBlank()) {
            paymentCell.add(new Paragraph("M-PESA Paybill: " + mpesaPaybill)
                    .setFont(boldFont)
                    .setFontSize(11));
            paymentCell.add(new Paragraph("Account Number: " + invoice.getInvoiceNumber())
                    .setFont(boldFont)
                    .setFontSize(11));
        }
        
        // Bank payment info from settings
        String bankName = settings.getBankName();
        String bankAccountName = settings.getBankAccountName();
        String bankAccountNumber = settings.getBankAccountNumber();
        
        if (bankName != null && !bankName.isBlank()) {
            if (mpesaPaybill != null && !mpesaPaybill.isBlank()) {
                paymentCell.add(new Paragraph("\nAlternatively, you can make a bank transfer to:")
                        .setFont(regularFont)
                        .setFontSize(10)
                        .setMarginTop(10));
            } else {
                paymentCell.add(new Paragraph("Bank Transfer:")
                        .setFont(regularFont)
                        .setFontSize(10));
            }
            paymentCell.add(new Paragraph("Bank: " + bankName)
                    .setFont(regularFont)
                    .setFontSize(10));
            if (bankAccountName != null && !bankAccountName.isBlank()) {
                paymentCell.add(new Paragraph("Account Name: " + bankAccountName)
                        .setFont(regularFont)
                        .setFontSize(10));
            }
            if (bankAccountNumber != null && !bankAccountNumber.isBlank()) {
                paymentCell.add(new Paragraph("Account Number: " + bankAccountNumber)
                        .setFont(regularFont)
                        .setFontSize(10));
            }
        }
        
        // If no payment info configured, show placeholder
        if ((mpesaPaybill == null || mpesaPaybill.isBlank()) && (bankName == null || bankName.isBlank())) {
            paymentCell.add(new Paragraph("Payment details will be provided separately.")
                    .setFont(regularFont)
                    .setFontSize(10)
                    .setFontColor(DARK_GRAY));
        }

        paymentTable.addCell(paymentCell);
        document.add(paymentTable);

        // Notes (if any)
        if (invoice.getNotes() != null && !invoice.getNotes().isBlank()) {
            document.add(new Paragraph("Notes:")
                    .setFont(boldFont)
                    .setFontSize(10)
                    .setMarginTop(10));
            document.add(new Paragraph(invoice.getNotes())
                    .setFont(regularFont)
                    .setFontSize(10)
                    .setFontColor(DARK_GRAY));
        }
    }

    private void addFooter(Document document, PdfFont font, SystemSettings settings) {
        document.add(new Paragraph("\nThank you for your business!")
                .setFont(font)
                .setFontSize(10)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(DARK_GRAY)
                .setMarginTop(30));

        // Contact email from settings
        String contactEmail = settings.getPlatformEmail();
        if (contactEmail == null || contactEmail.isBlank()) {
            contactEmail = "support@calpex.co.ke";
        }
        
        document.add(new Paragraph("If you have any questions about this invoice, please contact " + contactEmail)
                .setFont(font)
                .setFontSize(8)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(DARK_GRAY));
    }

    private Cell createHeaderCell(String text, PdfFont font) {
        return new Cell()
                .add(new Paragraph(text).setFont(font).setFontSize(9))
                .setBackgroundColor(PRIMARY_COLOR)
                .setFontColor(ColorConstants.WHITE)
                .setPadding(8)
                .setBorder(Border.NO_BORDER);
    }

    private Cell createDataCell(String text, PdfFont font) {
        return new Cell()
                .add(new Paragraph(text).setFont(font).setFontSize(8))
                .setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(LIGHT_GRAY, 0.5f))
                .setPadding(5);
    }

    private DeviceRgb getStatusColor(Invoice.InvoiceStatus status) {
        return switch (status) {
            case PAID -> new DeviceRgb(34, 197, 94);      // Green
            case SENT -> new DeviceRgb(59, 130, 246);     // Blue
            case DRAFT -> new DeviceRgb(156, 163, 175);   // Gray
            case PARTIAL -> new DeviceRgb(251, 191, 36);  // Yellow
            case OVERDUE -> new DeviceRgb(239, 68, 68);   // Red
            case PENDING_CONFIRMATION -> new DeviceRgb(37, 99, 168); // Dark Blue
            case CANCELLED, VOID -> new DeviceRgb(107, 114, 128); // Dark gray
        };
    }

    private String formatDate(java.time.LocalDate date) {
        return date != null ? date.format(DATE_FORMAT) : "";
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "KES 0.00";
        return "KES " + String.format("%,.2f", amount);
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength - 3) + "...";
    }
}
