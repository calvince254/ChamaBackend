package com.calpex_technologies.chama.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "investment_value_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class InvestmentValueHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investment_id", nullable = false)
    private Investment investment;

    @Column(name = "value_date", nullable = false)
    private LocalDate valueDate;

    @Column(name = "recorded_value", precision = 15, scale = 2, nullable = false)
    private BigDecimal recordedValue;

    @Column(name = "previous_value", precision = 15, scale = 2)
    private BigDecimal previousValue;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "recorded_by")
    private String recordedBy;

    // Computed field for value change
    public BigDecimal getValueChange() {
        if (previousValue == null) {
            return BigDecimal.ZERO;
        }
        return recordedValue.subtract(previousValue);
    }

    // Computed field for percentage change
    public BigDecimal getPercentageChange() {
        if (previousValue == null || previousValue.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return getValueChange()
                .multiply(BigDecimal.valueOf(100))
                .divide(previousValue, 2, java.math.RoundingMode.HALF_UP);
    }
}
