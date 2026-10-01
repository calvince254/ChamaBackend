package com.calpex_technologies.chama.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "welfare_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class WelfareEvent extends TenantAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType eventType;

    private String relation;

    @Column(name = "date_reported", nullable = false)
    private LocalDate dateReported;

    @Column(name = "amount_per_member", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal amountPerMember = BigDecimal.ZERO;

    @Column(name = "total_expected", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalExpected = BigDecimal.ZERO;

    @Column(name = "total_collected", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalCollected = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private WelfareStatus status = WelfareStatus.OPEN;

    @Column(columnDefinition = "TEXT")
    private String description;

    // Whether all active members are involved in this welfare event
    @Column(name = "all_members_involved")
    @Builder.Default
    private Boolean allMembersInvolved = true;

    // Welfare contributions (payments) from members
    @OneToMany(mappedBy = "welfareEvent", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<WelfareContribution> contributions = new ArrayList<>();

    // Contribution types that can be used to fund this welfare event
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "welfare_funding_sources",
        joinColumns = @JoinColumn(name = "welfare_event_id"),
        inverseJoinColumns = @JoinColumn(name = "contribution_type_id")
    )
    @Builder.Default
    private Set<ContributionType> fundingContributionTypes = new HashSet<>();

    public enum EventType {
        BEREAVEMENT, ILLNESS, MARRIAGE, OTHER
    }

    public enum WelfareStatus {
        OPEN, PARTIALLY_DISBURSED, DISBURSED, CLOSED, CANCELLED
    }
}
