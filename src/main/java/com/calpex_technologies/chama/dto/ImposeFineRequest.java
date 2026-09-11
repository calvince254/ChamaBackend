package com.calpex_technologies.chama.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImposeFineRequest {
    private Long memberId;
    private Long fineTypeId;
    private String notes;
}
