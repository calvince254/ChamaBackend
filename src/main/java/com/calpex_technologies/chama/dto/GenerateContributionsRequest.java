package com.calpex_technologies.chama.dto;

import lombok.Data;
import java.util.List;

@Data
public class GenerateContributionsRequest {
    private Long contributionTypeId;
    private String month;
    private List<Long> memberIds; // Empty or null = all active members
}
