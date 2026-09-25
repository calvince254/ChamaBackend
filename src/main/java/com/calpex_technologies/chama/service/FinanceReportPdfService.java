package com.calpex_technologies.chama.service;

import com.calpex_technologies.chama.dto.FinanceSummaryDto;
import com.calpex_technologies.chama.dto.TransactionDto;
import com.calpex_technologies.chama.entity.Tenant;
import com.calpex_technologies.chama.repository.TenantRepository;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
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
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service for generating Finance Report PDFs with Calpex branding.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FinanceReportPdfService {

    private final TenantRepository tenantRepository;
    private final FinanceService financeService;

    // System colors - only green and gray
    private static final DeviceRgb HEADER_BG = new DeviceRgb(30, 70, 50);       // Dark green header
    private static final DeviceRgb PRIMARY_GREEN = new DeviceRgb(22, 101, 52);  // Primary green
    private static final DeviceRgb LIGHT_GREEN = new DeviceRgb(74, 222, 128);   // Light green for chama name
    private static final DeviceRgb LIGHT_GRAY = new DeviceRgb(245, 245, 245);   // Light background
    private static final DeviceRgb WHITE = new DeviceRgb(255, 255, 255);        // White
    private static final DeviceRgb BORDER_GRAY = new DeviceRgb(220, 220, 220);  // Border color
    private static final DeviceRgb TEXT_DARK = new DeviceRgb(51, 51, 51);       // Dark text
    private static final DeviceRgb TEXT_MUTED = new DeviceRgb(102, 102, 102);   // Muted text
    private static final DeviceRgb BALANCE_BG = new DeviceRgb(240, 253, 244);   // Light green background
    
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy");

    /**
     * Generate a Finance Report PDF.
     */
    public byte[] generateFinanceReportPdf(
            Long tenantId,
            LocalDate startDate,
            LocalDate endDate,
            String type,
            String direction,
            Long memberId) {
        
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf, PageSize.A4);
            document.setMargins(0, 0, 30, 0);

            PdfFont regularFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);

            Tenant tenant = tenantRepository.findById(tenantId)
                    .orElseThrow(() -> new RuntimeException("Tenant not found"));

            FinanceSummaryDto summary = financeService.getFinanceSummary(tenantId);

            List<TransactionDto> transactions = financeService.getTransactionsFiltered(
                    tenantId, startDate, endDate, type, direction, memberId);

            BigDecimal totalCredits = transactions.stream()
                    .filter(t -> "IN".equals(t.getDirection()))
                    .map(TransactionDto::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalDebits = transactions.stream()
                    .filter(t -> "OUT".equals(t.getDirection()))
                    .map(TransactionDto::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal netBalance = totalCredits.subtract(totalDebits);

            addHeader(document, tenant, boldFont, regularFont, startDate, endDate);
            addFinancialSummary(document, summary, totalCredits, totalDebits, netBalance, boldFont, regularFont);
            addTransactionTable(document, transactions, boldFont, regularFont);
            addFooter(document, regularFont);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Error generating finance report PDF for tenant {}: {}", tenantId, e.getMessage(), e);
            throw new RuntimeException("Failed to generate finance report PDF", e);
        }
    }

    private void addHeader(Document document, Tenant tenant, PdfFont boldFont, PdfFont regularFont,
                           LocalDate startDate, LocalDate endDate) {
        Table headerTable = new Table(UnitValue.createPercentArray(new float[]{1}))
                .setWidth(UnitValue.createPercentValue(100))
                .setBackgroundColor(HEADER_BG);

        Table innerHeader = new Table(UnitValue.createPercentArray(new float[]{3f, 3f}))
                .setWidth(UnitValue.createPercentValue(100));

        // Left side - Chama name
        Cell leftCell = new Cell().setBorder(Border.NO_BORDER).setPadding(20).setVerticalAlignment(VerticalAlignment.MIDDLE);
        leftCell.add(new Paragraph("CALPEX CHAMA MANAGER")
                .setFont(regularFont).setFontSize(8).setFontColor(ColorConstants.WHITE).setMarginBottom(2));
        leftCell.add(new Paragraph(tenant.getName())
                .setFont(boldFont).setFontSize(14).setFontColor(LIGHT_GREEN));
        innerHeader.addCell(leftCell);

        // Right side - Title and period
        Cell rightCell = new Cell().setBorder(Border.NO_BORDER).setPadding(20)
                .setTextAlignment(TextAlignment.RIGHT).setVerticalAlignment(VerticalAlignment.MIDDLE);
        
        String periodText = "All Time";
        if (startDate != null && endDate != null) {
            periodText = startDate.format(DATE_FORMAT) + " – " + endDate.format(DATE_FORMAT);
        } else if (startDate != null) {
            periodText = "From " + startDate.format(DATE_FORMAT);
        } else if (endDate != null) {
            periodText = "Until " + endDate.format(DATE_FORMAT);
        }

        Table titleTable = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                .setWidth(UnitValue.createPercentValue(100));
        
        Cell labelCell = new Cell().setBorder(Border.NO_BORDER).setTextAlignment(TextAlignment.RIGHT)
                .setVerticalAlignment(VerticalAlignment.MIDDLE);
        labelCell.add(new Paragraph("FINANCIAL STATEMENT")
                .setFont(boldFont).setFontSize(10).setFontColor(ColorConstants.WHITE));
        titleTable.addCell(labelCell);

        Cell badgeCell = new Cell().setBorder(Border.NO_BORDER).setTextAlignment(TextAlignment.RIGHT)
                .setVerticalAlignment(VerticalAlignment.MIDDLE).setPaddingLeft(8);
        Paragraph badge = new Paragraph(periodText)
                .setFont(regularFont).setFontSize(8).setFontColor(ColorConstants.WHITE)
                .setBackgroundColor(new DeviceRgb(55, 100, 75))
                .setPadding(4).setPaddingLeft(8).setPaddingRight(8);
        badgeCell.add(badge);
        titleTable.addCell(badgeCell);
        
        rightCell.add(titleTable);
        innerHeader.addCell(rightCell);

        Cell headerContent = new Cell().add(innerHeader).setBorder(Border.NO_BORDER).setPadding(0);
        headerTable.addCell(headerContent);
        document.add(headerTable);
    }

    private void addFinancialSummary(Document document, FinanceSummaryDto summary,
                                      BigDecimal totalCredits, BigDecimal totalDebits, BigDecimal netBalance,
                                      PdfFont boldFont, PdfFont regularFont) {
        // Section header
        Table sectionHeader = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginTop(20).setMarginBottom(10).setMarginLeft(30).setMarginRight(30);

        Cell titleCell = new Cell().setBorder(Border.NO_BORDER);
        titleCell.add(new Paragraph("FINANCIAL SUMMARY")
                .setFont(boldFont).setFontSize(11).setFontColor(TEXT_DARK));
        sectionHeader.addCell(titleCell);

        Cell dateCell = new Cell().setBorder(Border.NO_BORDER).setTextAlignment(TextAlignment.RIGHT);
        dateCell.add(new Paragraph("Report generated " + LocalDate.now().format(DATE_FORMAT))
                .setFont(regularFont).setFontSize(8).setFontColor(TEXT_MUTED));
        sectionHeader.addCell(dateCell);

        document.add(sectionHeader);

        // Summary cards
        Table summaryCards = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1, 1}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginLeft(30).setMarginRight(30);

        addSummaryCard(summaryCards, "Total Savings", summary.getTotalSavings(), PRIMARY_GREEN, boldFont, regularFont);
        addSummaryCard(summaryCards, "Loans Outstanding", summary.getTotalLoansOutstanding(), TEXT_DARK, boldFont, regularFont);
        addSummaryCard(summaryCards, "Total Credits", totalCredits, PRIMARY_GREEN, boldFont, regularFont);
        addSummaryCard(summaryCards, "Total Debits", totalDebits, TEXT_DARK, boldFont, regularFont);

        document.add(summaryCards);

        // Net Balance box
        Table balanceBox = new Table(UnitValue.createPercentArray(new float[]{1}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginLeft(30).setMarginRight(30).setMarginTop(12);

        Cell balanceCell = new Cell()
                .setBackgroundColor(BALANCE_BG)
                .setBorder(new SolidBorder(PRIMARY_GREEN, 1))
                .setPadding(12)
                .setTextAlignment(TextAlignment.CENTER);

        balanceCell.add(new Paragraph("NET BALANCE (PERIOD)")
                .setFont(regularFont).setFontSize(8).setFontColor(TEXT_MUTED).setMarginBottom(4));
        balanceCell.add(new Paragraph(formatCurrency(netBalance))
                .setFont(boldFont).setFontSize(18).setFontColor(PRIMARY_GREEN).setMarginBottom(4));
        balanceCell.add(new Paragraph("Credits: " + formatCurrency(totalCredits) + "  |  Debits: " + formatCurrency(totalDebits))
                .setFont(regularFont).setFontSize(8).setFontColor(TEXT_MUTED));

        balanceBox.addCell(balanceCell);
        document.add(balanceBox);
    }

    private void addSummaryCard(Table table, String label, BigDecimal value, DeviceRgb labelColor,
                                 PdfFont boldFont, PdfFont regularFont) {
        Cell cell = new Cell()
                .setBackgroundColor(LIGHT_GRAY)
                .setBorder(new SolidBorder(BORDER_GRAY, 0.5f))
                .setPadding(10);

        cell.add(new Paragraph(label)
                .setFont(regularFont).setFontSize(8).setFontColor(labelColor).setMarginBottom(4));
        cell.add(new Paragraph(formatCurrency(value))
                .setFont(boldFont).setFontSize(11).setFontColor(TEXT_DARK));

        table.addCell(cell);
    }

    private void addTransactionTable(Document document, List<TransactionDto> transactions,
                                      PdfFont boldFont, PdfFont regularFont) {
        // Section header
        Table sectionHeader = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginLeft(30).setMarginRight(30).setMarginTop(20).setMarginBottom(8);

        Cell titleCell = new Cell().setBorder(Border.NO_BORDER);
        titleCell.add(new Paragraph("TRANSACTION DETAILS")
                .setFont(boldFont).setFontSize(11).setFontColor(TEXT_DARK));
        sectionHeader.addCell(titleCell);

        Cell countCell = new Cell().setBorder(Border.NO_BORDER).setTextAlignment(TextAlignment.RIGHT);
        countCell.add(new Paragraph(transactions.size() + " transactions")
                .setFont(regularFont).setFontSize(8).setFontColor(TEXT_MUTED));
        sectionHeader.addCell(countCell);

        document.add(sectionHeader);

        if (transactions.isEmpty()) {
            Table emptyTable = new Table(UnitValue.createPercentArray(new float[]{1}))
                    .setWidth(UnitValue.createPercentValue(100))
                    .setMarginLeft(30).setMarginRight(30);
            Cell emptyCell = new Cell().setBorder(Border.NO_BORDER).setPadding(20).setTextAlignment(TextAlignment.CENTER);
            emptyCell.add(new Paragraph("No transactions found for the selected criteria.")
                    .setFont(regularFont).setFontSize(9).setFontColor(TEXT_MUTED));
            emptyTable.addCell(emptyCell);
            document.add(emptyTable);
            return;
        }

        // Transaction table
        Table txTable = new Table(UnitValue.createPercentArray(new float[]{1f, 1.2f, 1f, 2.5f, 1f}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginLeft(30).setMarginRight(30);

        // Header row
        txTable.addHeaderCell(createTableHeader("Date", boldFont));
        txTable.addHeaderCell(createTableHeader("Member", boldFont));
        txTable.addHeaderCell(createTableHeader("Type", boldFont));
        txTable.addHeaderCell(createTableHeader("Description", boldFont));
        txTable.addHeaderCell(createTableHeader("Amount", boldFont).setTextAlignment(TextAlignment.RIGHT));

        // Data rows
        for (int i = 0; i < transactions.size(); i++) {
            TransactionDto tx = transactions.get(i);
            boolean isAltRow = i % 2 == 1;
            addTransactionRow(txTable, tx, regularFont, isAltRow);
        }

        document.add(txTable);
    }

    private Cell createTableHeader(String text, PdfFont font) {
        return new Cell()
                .add(new Paragraph(text).setFont(font).setFontSize(8))
                .setBackgroundColor(PRIMARY_GREEN)
                .setFontColor(ColorConstants.WHITE)
                .setPadding(8)
                .setBorder(Border.NO_BORDER);
    }

    private void addTransactionRow(Table table, TransactionDto tx, PdfFont regularFont, boolean altRow) {
        DeviceRgb bgColor = altRow ? LIGHT_GRAY : WHITE;
        boolean isIncome = "IN".equals(tx.getDirection());

        // Date
        table.addCell(createTableCell(tx.getDate() != null ? formatDateString(tx.getDate()) : "", regularFont, bgColor));
        
        // Member
        table.addCell(createTableCell(tx.getMemberName() != null ? tx.getMemberName() : "System", regularFont, bgColor));
        
        // Type - plain text, no colored badge
        table.addCell(createTableCell(formatType(tx.getType()), regularFont, bgColor));
        
        // Description - full text, no truncation
        table.addCell(createTableCell(tx.getDescription() != null ? tx.getDescription() : "", regularFont, bgColor));
        
        // Amount - green for income, dark for expense (no red)
        String amountText = (isIncome ? "+" : "-") + formatCurrency(tx.getAmount());
        Cell amountCell = new Cell()
                .add(new Paragraph(amountText).setFont(regularFont).setFontSize(8))
                .setBackgroundColor(bgColor)
                .setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(BORDER_GRAY, 0.5f))
                .setPadding(6)
                .setTextAlignment(TextAlignment.RIGHT)
                .setFontColor(isIncome ? PRIMARY_GREEN : TEXT_DARK);
        table.addCell(amountCell);
    }

    private Cell createTableCell(String text, PdfFont font, DeviceRgb bgColor) {
        return new Cell()
                .add(new Paragraph(text).setFont(font).setFontSize(8))
                .setBackgroundColor(bgColor)
                .setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(BORDER_GRAY, 0.5f))
                .setPadding(6)
                .setVerticalAlignment(VerticalAlignment.MIDDLE);
    }

    private void addFooter(Document document, PdfFont font) {
        document.add(new Paragraph("\n"));
        document.add(new Paragraph("Powered by Calpex Technologies • www.calpex.co.ke")
                .setFont(font)
                .setFontSize(8)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(PRIMARY_GREEN)
                .setMarginTop(20));
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "KES 0";
        return "KES " + String.format("%,.0f", amount);
    }

    private String formatDateString(String dateStr) {
        try {
            LocalDate date = LocalDate.parse(dateStr);
            return date.format(DATE_FORMAT);
        } catch (Exception e) {
            return dateStr;
        }
    }

    private String formatType(String type) {
        if (type == null) return "";
        return switch (type.toUpperCase()) {
            case "CONTRIBUTION" -> "Contribution";
            case "LOAN_DISBURSEMENT" -> "Loan";
            case "LOAN_REPAYMENT" -> "Repayment";
            case "LOAN_INTEREST" -> "Interest";
            case "PENALTY" -> "Penalty";
            case "FINE" -> "Fine";
            case "WELFARE" -> "Welfare";
            case "INVESTMENT" -> "Investment";
            case "SHARE_PAYOUT" -> "Payout";
            case "PAYOUT" -> "Payout";
            case "REGISTRATION" -> "Registration";
            case "MERRY_GO_ROUND" -> "MGR";
            case "PROCESSING_FEE" -> "Fee";
            default -> type.replace("_", " ");
        };
    }
}
