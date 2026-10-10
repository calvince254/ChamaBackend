package com.calpex_technologies.chama.service;

import com.calpex_technologies.chama.dto.ContributionReportItemDto;
import com.calpex_technologies.chama.entity.ContributionType;
import com.calpex_technologies.chama.entity.Member;
import com.calpex_technologies.chama.entity.Tenant;
import com.calpex_technologies.chama.repository.ContributionTypeRepository;
import com.calpex_technologies.chama.repository.MemberRepository;
import com.calpex_technologies.chama.repository.TenantRepository;
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
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Service for generating Contribution Report PDFs with Calpex branding.
 * This generates a PDF showing contributions directly (including PENDING ones).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContributionTransactionsPdfService {

    private final TenantRepository tenantRepository;
    private final MemberRepository memberRepository;
    private final ContributionTypeRepository contributionTypeRepository;
    private final ContributionService contributionService;

    // System colors
    private static final DeviceRgb HEADER_BG = new DeviceRgb(30, 70, 50);
    private static final DeviceRgb PRIMARY_GREEN = new DeviceRgb(22, 101, 52);
    private static final DeviceRgb LIGHT_GREEN = new DeviceRgb(74, 222, 128);
    private static final DeviceRgb LIGHT_GRAY = new DeviceRgb(245, 245, 245);
    private static final DeviceRgb WHITE = new DeviceRgb(255, 255, 255);
    private static final DeviceRgb BORDER_GRAY = new DeviceRgb(220, 220, 220);
    private static final DeviceRgb TEXT_DARK = new DeviceRgb(51, 51, 51);
    private static final DeviceRgb TEXT_MUTED = new DeviceRgb(102, 102, 102);
    private static final DeviceRgb DANGER_RED = new DeviceRgb(185, 28, 28);
    private static final DeviceRgb WARNING_YELLOW = new DeviceRgb(234, 179, 8);
    private static final DeviceRgb INFO_BLUE = new DeviceRgb(59, 130, 246);
    private static final DeviceRgb WARNING_ORANGE = new DeviceRgb(249, 115, 22);

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy");

    /**
     * Generate a Contributions Report PDF.
     * Shows contributions directly (including PENDING ones).
     * Supports optional status filter: PENDING, PAID, PARTIAL, OVERDUE, DEFAULTED, or null/ALL for all.
     */
    public byte[] generateContributionsReportPdf(
            Long tenantId,
            LocalDate startDate,
            LocalDate endDate,
            Long memberId,
            Long contributionTypeId,
            String status) {

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf, PageSize.A4);
            document.setMargins(0, 0, 30, 0);

            PdfFont regularFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);

            Tenant tenant = tenantRepository.findById(tenantId)
                    .orElseThrow(() -> new RuntimeException("Tenant not found"));

            // Get filter names for display
            String memberName = null;
            if (memberId != null) {
                memberName = memberRepository.findByIdAndTenantId(memberId, tenantId)
                        .map(Member::getName)
                        .orElse(null);
            }

            String contributionTypeName = null;
            if (contributionTypeId != null) {
                contributionTypeName = contributionTypeRepository.findByIdAndTenantId(contributionTypeId, tenantId)
                        .map(ContributionType::getName)
                        .orElse(null);
            }
            
            // Format status for display
            String statusDisplay = formatStatusForDisplay(status);

            // Get contributions with filters applied (including status)
            List<ContributionReportItemDto> contributions = contributionService.getAllContributionsForReportWithStatus(
                    tenantId, contributionTypeId, memberId, startDate, endDate, status);

            // Get summary with same filters (including status)
            Map<String, Object> summary = contributionService.getContributionsReportSummaryWithStatus(
                    tenantId, contributionTypeId, memberId, startDate, endDate, status);

            BigDecimal totalDue = (BigDecimal) summary.get("totalDue");
            BigDecimal totalPaid = (BigDecimal) summary.get("totalPaid");
            BigDecimal balance = (BigDecimal) summary.get("balance");
            Long recordCount = (Long) summary.get("count");

            addHeader(document, tenant, boldFont, regularFont, startDate, endDate, memberName, contributionTypeName, statusDisplay);
            addSummary(document, totalDue, totalPaid, balance, recordCount.intValue(), boldFont, regularFont);
            addContributionsTable(document, contributions, boldFont, regularFont);
            addFooter(document, regularFont);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Error generating contributions report PDF for tenant {}: {}", tenantId, e.getMessage(), e);
            throw new RuntimeException("Failed to generate contributions report PDF", e);
        }
    }

    private void addHeader(Document document, Tenant tenant, PdfFont boldFont, PdfFont regularFont,
                           LocalDate startDate, LocalDate endDate, String memberName, String contributionTypeName,
                           String statusDisplay) {
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
        labelCell.add(new Paragraph("CONTRIBUTIONS REPORT")
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

        // Add filter info below
        StringBuilder filterInfo = new StringBuilder();
        if (memberName != null) {
            filterInfo.append("Member: ").append(memberName);
        }
        if (contributionTypeName != null) {
            if (filterInfo.length() > 0) filterInfo.append(" | ");
            filterInfo.append("Type: ").append(contributionTypeName);
        }
        if (statusDisplay != null) {
            if (filterInfo.length() > 0) filterInfo.append(" | ");
            filterInfo.append("Status: ").append(statusDisplay);
        }
        if (filterInfo.length() > 0) {
            rightCell.add(new Paragraph(filterInfo.toString())
                    .setFont(regularFont).setFontSize(8).setFontColor(ColorConstants.WHITE)
                    .setMarginTop(4));
        }

        innerHeader.addCell(rightCell);

        Cell headerContent = new Cell().add(innerHeader).setBorder(Border.NO_BORDER).setPadding(0);
        headerTable.addCell(headerContent);
        document.add(headerTable);
    }

    private void addSummary(Document document, BigDecimal totalDue, BigDecimal totalPaid,
                            BigDecimal balance, int recordCount, PdfFont boldFont, PdfFont regularFont) {
        // Section header
        Table sectionHeader = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginTop(20).setMarginBottom(10).setMarginLeft(30).setMarginRight(30);

        Cell titleCell = new Cell().setBorder(Border.NO_BORDER);
        titleCell.add(new Paragraph("SUMMARY")
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

        addSummaryCard(summaryCards, "Records", String.valueOf(recordCount), TEXT_DARK, boldFont, regularFont);
        addSummaryCard(summaryCards, "Total Due", formatCurrency(totalDue), TEXT_DARK, boldFont, regularFont);
        addSummaryCard(summaryCards, "Total Paid", formatCurrency(totalPaid), PRIMARY_GREEN, boldFont, regularFont);
        addSummaryCard(summaryCards, "Balance", formatCurrency(balance), 
                balance.compareTo(BigDecimal.ZERO) > 0 ? DANGER_RED : PRIMARY_GREEN, boldFont, regularFont);

        document.add(summaryCards);
    }

    private void addSummaryCard(Table table, String label, String value, DeviceRgb valueColor,
                                 PdfFont boldFont, PdfFont regularFont) {
        Cell cell = new Cell()
                .setBackgroundColor(LIGHT_GRAY)
                .setBorder(new SolidBorder(BORDER_GRAY, 0.5f))
                .setPadding(10);

        cell.add(new Paragraph(label)
                .setFont(regularFont).setFontSize(8).setFontColor(TEXT_MUTED).setMarginBottom(4));
        cell.add(new Paragraph(value)
                .setFont(boldFont).setFontSize(11).setFontColor(valueColor));

        table.addCell(cell);
    }



    private Cell createTableHeader(String text, PdfFont font) {
        return new Cell()
                .add(new Paragraph(text).setFont(font).setFontSize(8))
                .setBackgroundColor(PRIMARY_GREEN)
                .setFontColor(ColorConstants.WHITE)
                .setPadding(8)
                .setBorder(Border.NO_BORDER);
    }

    private void addContributionRow(Table table, ContributionReportItemDto c, PdfFont regularFont, PdfFont boldFont, boolean altRow) {
        DeviceRgb bgColor = altRow ? LIGHT_GRAY : WHITE;

        // Month (e.g., "2026-09")
        table.addCell(createTableCell(c.getMonth() != null ? c.getMonth() : "", regularFont, bgColor));

        // Member
        String memberDisplay = c.getMemberName() != null ? c.getMemberName() : "";
        if (c.getMemberNo() != null && !c.getMemberNo().isEmpty()) {
            memberDisplay += " (" + c.getMemberNo() + ")";
        }
        table.addCell(createTableCell(memberDisplay, regularFont, bgColor));

        // Type
        table.addCell(createTableCell(c.getContributionTypeName() != null ? c.getContributionTypeName() : "", regularFont, bgColor));

        // Due Date
        String dueDateDisplay = c.getDueDate();
        if (dueDateDisplay != null) {
            try {
                LocalDate dueDate = LocalDate.parse(dueDateDisplay);
                dueDateDisplay = dueDate.format(DATE_FORMAT);
            } catch (Exception ignored) {}
        } else {
            dueDateDisplay = "-";
        }
        table.addCell(createTableCell(dueDateDisplay, regularFont, bgColor));

        // Amount Due
        table.addCell(createAmountCell(c.getAmountDue(), regularFont, bgColor, TEXT_DARK));

        // Amount Paid
        table.addCell(createAmountCell(c.getAmountPaid(), regularFont, bgColor, PRIMARY_GREEN));

        // Balance
        BigDecimal balance = c.getBalance() != null ? c.getBalance() : BigDecimal.ZERO;
        DeviceRgb balanceColor = balance.compareTo(BigDecimal.ZERO) > 0 ? DANGER_RED : PRIMARY_GREEN;
        table.addCell(createAmountCell(balance, regularFont, bgColor, balanceColor));

        // Status
        String statusDisplay = formatStatusDisplay(c.getStatus());
        DeviceRgb statusColor = getStatusColor(c.getStatus());
        Cell statusCell = new Cell()
                .add(new Paragraph(statusDisplay).setFont(regularFont).setFontSize(7))
                .setBackgroundColor(bgColor)
                .setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(BORDER_GRAY, 0.5f))
                .setPadding(6)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(statusColor);
        table.addCell(statusCell);
    }
    
    private String formatStatusDisplay(String status) {
        if (status == null) return "-";
        switch (status.toUpperCase()) {
            case "PENDING": return "Pending";
            case "PAID": return "Paid";
            case "PARTIAL": return "Partial";
            case "OVERDUE": return "Overdue";
            case "DEFAULTED": return "Defaulted";
            default: return status;
        }
    }
    
    private DeviceRgb getStatusColor(String status) {
        if (status == null) return TEXT_MUTED;
        switch (status.toUpperCase()) {
            case "PAID": return PRIMARY_GREEN;
            case "PENDING": return WARNING_YELLOW;
            case "PARTIAL": return INFO_BLUE;
            case "OVERDUE": return WARNING_ORANGE;
            case "DEFAULTED": return DANGER_RED;
            default: return TEXT_MUTED;
        }
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

    private Cell createAmountCell(BigDecimal amount, PdfFont font, DeviceRgb bgColor, DeviceRgb textColor) {
        return new Cell()
                .add(new Paragraph(formatCurrency(amount)).setFont(font).setFontSize(8))
                .setBackgroundColor(bgColor)
                .setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(BORDER_GRAY, 0.5f))
                .setPadding(6)
                .setTextAlignment(TextAlignment.RIGHT)
                .setFontColor(textColor);
    }

    private void addFooter(Document document, PdfFont font) {
        document.add(new Paragraph("\n"));
        document.add(new Paragraph("Powered by Calpex Technologies • www.calpextechnologies.co.ke")
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
    
    /**
     * Format status string for display in PDF.
     * Returns null if status is null, empty, or "ALL".
     */
    private String formatStatusForDisplay(String status) {
        if (status == null || status.isEmpty() || "ALL".equalsIgnoreCase(status)) {
            return null;
        }
        // Convert to title case for display
        switch (status.toUpperCase()) {
            case "PENDING": return "Pending";
            case "PAID": return "Paid";
            case "PARTIAL": return "Partial";
            case "OVERDUE": return "Overdue";
            case "DEFAULTED": return "Defaulted";
            default: return status;
        }
    }
}
