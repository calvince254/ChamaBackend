package com.calpex_technologies.chama.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "shares")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Share extends TenantAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "shares_owned", nullable = false)
    @Builder.Default
    private int sharesOwned = 0;

    @Column(name = "share_value", precision = 15, scale = 2, nullable = false)
    private BigDecimal shareValue;

    @Column(name = "total_value", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalValue = BigDecimal.ZERO;

    @Column(name = "last_purchase_date")
    private LocalDate lastPurchaseDate;
}
