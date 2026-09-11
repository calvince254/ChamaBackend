package com.calpex_technologies.chama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionDto {
    private String module;
    private boolean view;
    private boolean create;
    private boolean edit;
    private boolean approve;
    private boolean delete;
}
