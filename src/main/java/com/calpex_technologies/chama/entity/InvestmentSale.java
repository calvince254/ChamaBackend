package com.calpex_technologies.chama.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entity to track individual sales (partial or full) of an investment.
 * Each sale records: units sold, sale price, principal portion, and realized gain/loss.
 */
@Entity
@Table(name = "investment_sales")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class InvestmentSale extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investment_id", nullable = false)
    private Investment investment;

    @Column(name = "sale_date", nullable = false)
    private LocalDate saleDate;

    @Column(name = "units_sold", precision = 15, scale = 4, nullable = false)
    private BigDecimal unitsSold;

    @Column(name = "units_remaining_after", precision = 15, scale = 4, nullable = false)
    private BigDecimal unitsRemainingAfter;

    @Column(name = "sale_price_per_unit", precision = 15, scale = 4)
    private BigDecimal salePricePerUnit;

    @Column(name = "total_sale_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalSaleAmount;

    @Column(name = "principal_portion", precision = 15, scale = 2, nullable = false)
    private BigDecimal principalPortion; // Cost basis of sold units

    @Column(name = "realized_gain", precision = 15, scale = 2, nullable = false)
    private BigDecimal realizedGain; // Profit/Loss = totalSaleAmount - principalPortion

    @Column(name = "is_full_sale")
    @Builder.Default
    private Boolean isFullSale = false;

    @Column(name = "sold_by")
    private String soldBy;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "recorded_at")
    private LocalDateTime recordedAt;

    // Computed: Gain percentage
    public BigDecimal getGainPercentage() {
        if (principalPortion == null || principalPortion.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return realizedGain
                .multiply(BigDecimal.valueOf(100))
                .divide(principalPortion, 2, java.math.RoundingMode.HALF_UP);
    }
}
