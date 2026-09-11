package com.calpex_technologies.chama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WelfareEventDto {
    private Long id;
    private Long memberId;
    private String memberName;
    private String eventType;
    private String relation;
    private String dateReported;
    private BigDecimal amountPerMember;
    private BigDecimal totalExpected;
    private BigDecimal totalCollected;
    private String status;
    private String description;
    
    // New fields for member selection
    private Boolean allMembersInvolved;
    private List<Long> involvedMemberIds;  // Used when allMembersInvolved = false
    
    // Contribution stats
    private Integer totalInvolved;
    private Integer paidCount;
}
