package com.calpex_technologies.chama.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberDto {
    private Long id;
    private String name;
    private String phone;
    private String email;
    private String memberNo;
    private String joinDate;
    private String status;
    private String role;
    private Long roleId;
    private BigDecimal totalSavings;
    private BigDecimal totalShares;
    private BigDecimal outstandingLoan;
    private int arrearsMonths;
    
    @JsonProperty("roleId")
    public Long getRoleId() {
        return roleId;
    }
}
