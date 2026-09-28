package com.calpex_technologies.chama.service;

import com.calpex_technologies.chama.dto.ContributionDto;
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
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service for generating Contribution Report PDFs with Calpex branding.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContributionReportPdfService {

    private final TenantRepository tenantRepository;
    private final MemberRepository memberRepository;
    private final ContributionTypeRepository contributionTypeRepository;
    private final ContributionService contributionService;

    // System colors - only green and gray
    private static final DeviceRgb HEADER_BG = new DeviceRgb(30, 70, 50);
    private static final DeviceRgb PRIMARY_GREEN = new DeviceRgb(22, 101, 52);
    private static final DeviceRgb LIGHT_GREEN = new DeviceRgb(74, 222, 128);
    private static final DeviceRgb LIGHT_GRAY = new DeviceRgb(245, 245, 245);
    private static final DeviceRgb WHITE = new DeviceRgb(255, 255, 255);
    private static final DeviceRgb BORDER_GRAY = new DeviceRgb(220, 220, 220);
    private static final DeviceRgb TEXT_DARK = new DeviceRgb(51, 51, 51);
    private static final DeviceRgb TEXT_MUTED = new DeviceRgb(102, 102, 102);
    private static final DeviceRgb WARNING_ORANGE = new DeviceRgb(217, 119, 6);
    private static final DeviceRgb DANGER_RED = new DeviceRgb(185, 28, 28);

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy");
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("MMM yyyy");

    /**
     * Generate a Contribution Report PDF.
     */
    public byte[] generateContributionReportPdf(
            Long tenantId,
            LocalDate startDate,
            LocalDate endDate,
            Long memberId,
            Long contributionTypeId) {

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

            // Get contributions
            List<ContributionDto> contributions = contributionService.getAllForReport(
                    tenantId, startDate, endDate, memberId, contributionTypeId);

            // Calculate totals
            BigDecimal totalDue = contributions.stream()
                    .map(ContributionDto::getAmountDue)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalPaid = contributions.stream()
                    .map(ContributionDto::getAmountPaid)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            long paidCount = contributions.stream()
                    .filter(c -> "PAID".equals(c.getStatus()))
                    .count();

            addHeader(document, tenant, boldFont, regularFont, startDate, endDate, memberName, contributionTypeName);
            addSummary(document, totalDue, totalPaid, contributions.size(), paidCount, boldFont, regularFont);
            addContributionTable(document, contributions, boldFont, regularFont);
            addFooter(document, regularFont);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Error generating contribution report PDF for tenant {}: {}", tenantId, e.getMessage(), e);
            throw new RuntimeException("Failed to generate contribution report PDF", e);
        }
    }

    private void addHeader(Document document, Tenant tenant, PdfFont boldFont, PdfFont regularFont,
                           LocalDate startDate, LocalDate endDate, String memberName, String contributionTypeName) {
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
        labelCell.add(new Paragraph("CONTRIBUTION REPORT")
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
                            int totalCount, long paidCount, PdfFont boldFont, PdfFont regularFont) {
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

        addSummaryCard(summaryCards, "Total Records", String.valueOf(totalCount), TEXT_DARK, boldFont, regularFont);
        addSummaryCard(summaryCards, "Total Due", formatCurrency(totalDue), TEXT_DARK, boldFont, regularFont);
        addSummaryCard(summaryCards, "Total Paid", formatCurrency(totalPaid), PRIMARY_GREEN, boldFont, regularFont);
        
        BigDecimal outstanding = totalDue.subtract(totalPaid);
        addSummaryCard(summaryCards, "Outstanding", formatCurrency(outstanding), 
                outstanding.compareTo(BigDecimal.ZERO) > 0 ? DANGER_RED : PRIMARY_GREEN, boldFont, regularFont);

        document.add(summaryCards);

        // Progress info
        if (totalCount > 0) {
            int collectionRate = totalDue.compareTo(BigDecimal.ZERO) > 0 
                    ? totalPaid.multiply(BigDecimal.valueOf(100)).divide(totalDue, 0, java.math.RoundingMode.HALF_UP).intValue()
                    : 100;

            Table progressBox = new Table(UnitValue.createPercentArray(new float[]{1}))
                    .setWidth(UnitValue.createPercentValue(100))
                    .setMarginLeft(30).setMarginRight(30).setMarginTop(12);

            Cell progressCell = new Cell()
                    .setBackgroundColor(LIGHT_GRAY)
                    .setBorder(new SolidBorder(BORDER_GRAY, 0.5f))
                    .setPadding(12)
                    .setTextAlignment(TextAlignment.CENTER);

            progressCell.add(new Paragraph(paidCount + " of " + totalCount + " contributions fully paid (" + collectionRate + "% collection rate)")
                    .setFont(regularFont).setFontSize(9).setFontColor(TEXT_MUTED));

            progressBox.addCell(progressCell);
            document.add(progressBox);
        }
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

    private void addContributionTable(Document document, List<ContributionDto> contributions,
                                       PdfFont boldFont, PdfFont regularFont) {
        // Section header
        Table sectionHeader = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginLeft(30).setMarginRight(30).setMarginTop(20).setMarginBottom(8);

        Cell titleCell = new Cell().setBorder(Border.NO_BORDER);
        titleCell.add(new Paragraph("CONTRIBUTION DETAILS")
                .setFont(boldFont).setFontSize(11).setFontColor(TEXT_DARK));
        sectionHeader.addCell(titleCell);

        Cell countCell = new Cell().setBorder(Border.NO_BORDER).setTextAlignment(TextAlignment.RIGHT);
        countCell.add(new Paragraph(contributions.size() + " records")
                .setFont(regularFont).setFontSize(8).setFontColor(TEXT_MUTED));
        sectionHeader.addCell(countCell);

        document.add(sectionHeader);

        if (contributions.isEmpty()) {
            Table emptyTable = new Table(UnitValue.createPercentArray(new float[]{1}))
                    .setWidth(UnitValue.createPercentValue(100))
                    .setMarginLeft(30).setMarginRight(30);
            Cell emptyCell = new Cell().setBorder(Border.NO_BORDER).setPadding(20).setTextAlignment(TextAlignment.CENTER);
            emptyCell.add(new Paragraph("No contributions found for the selected criteria.")
                    .setFont(regularFont).setFontSize(9).setFontColor(TEXT_MUTED));
            emptyTable.addCell(emptyCell);
            document.add(emptyTable);
            return;
        }

        // Contribution table
        Table ctTable = new Table(UnitValue.createPercentArray(new float[]{1.2f, 1.5f, 1.2f, 1f, 1f, 0.8f}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginLeft(30).setMarginRight(30);

        // Header row
        ctTable.addHeaderCell(createTableHeader("Period", boldFont));
        ctTable.addHeaderCell(createTableHeader("Member", boldFont));
        ctTable.addHeaderCell(createTableHeader("Type", boldFont));
        ctTable.addHeaderCell(createTableHeader("Due", boldFont).setTextAlignment(TextAlignment.RIGHT));
        ctTable.addHeaderCell(createTableHeader("Paid", boldFont).setTextAlignment(TextAlignment.RIGHT));
        ctTable.addHeaderCell(createTableHeader("Status", boldFont).setTextAlignment(TextAlignment.CENTER));

        // Data rows
        for (int i = 0; i < contributions.size(); i++) {
            ContributionDto c = contributions.get(i);
            boolean isAltRow = i % 2 == 1;
            addContributionRow(ctTable, c, regularFont, boldFont, isAltRow);
        }

        document.add(ctTable);
    }

    private Cell createTableHeader(String text, PdfFont font) {
        return new Cell()
                .add(new Paragraph(text).setFont(font).setFontSize(8))
                .setBackgroundColor(PRIMARY_GREEN)
                .setFontColor(ColorConstants.WHITE)
                .setPadding(8)
                .setBorder(Border.NO_BORDER);
    }

    private void addContributionRow(Table table, ContributionDto c, PdfFont regularFont, PdfFont boldFont, boolean altRow) {
        DeviceRgb bgColor = altRow ? LIGHT_GRAY : WHITE;

        // Period (month)
        String monthDisplay = c.getMonth();
        try {
            YearMonth ym = YearMonth.parse(c.getMonth());
            monthDisplay = ym.format(MONTH_FORMAT);
        } catch (Exception ignored) {}
        table.addCell(createTableCell(monthDisplay, regularFont, bgColor));

        // Member
        String memberDisplay = c.getMemberName();
        if (c.getMemberNo() != null && !c.getMemberNo().isEmpty()) {
            memberDisplay += " (" + c.getMemberNo() + ")";
        }
        table.addCell(createTableCell(memberDisplay, regularFont, bgColor));

        // Type
        table.addCell(createTableCell(c.getContributionTypeName() != null ? c.getContributionTypeName() : "", regularFont, bgColor));

        // Due
        table.addCell(createAmountCell(c.getAmountDue(), regularFont, bgColor, TEXT_DARK));

        // Paid
        table.addCell(createAmountCell(c.getAmountPaid(), regularFont, bgColor, PRIMARY_GREEN));

        // Status
        DeviceRgb statusColor = getStatusColor(c.getStatus());
        Cell statusCell = new Cell()
                .add(new Paragraph(c.getStatus()).setFont(regularFont).setFontSize(7))
                .setBackgroundColor(bgColor)
                .setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(BORDER_GRAY, 0.5f))
                .setPadding(6)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(statusColor);
        table.addCell(statusCell);
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

    private DeviceRgb getStatusColor(String status) {
        if (status == null) return TEXT_MUTED;
        return switch (status.toUpperCase()) {
            case "PAID" -> PRIMARY_GREEN;
            case "PARTIAL" -> WARNING_ORANGE;
            case "OVERDUE" -> DANGER_RED;
            case "PENDING" -> TEXT_MUTED;
            default -> TEXT_MUTED;
        };
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
}
