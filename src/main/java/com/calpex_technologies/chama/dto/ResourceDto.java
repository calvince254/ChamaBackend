package com.calpex_technologies.chama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceDto {
    private Long id;
    private String name;
    private String description;
    private String fileName;
    private String originalFileName;
    private Long fileSize;
    private String contentType;
    private String fileExtension;
    private Long uploadedById;
    private String uploadedByName;
    private Integer downloadCount;
    private Boolean active;
    private String createdAt;
    private String updatedAt;
}
