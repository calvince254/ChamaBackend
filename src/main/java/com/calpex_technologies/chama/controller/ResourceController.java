package com.calpex_technologies.chama.controller;

import com.calpex_technologies.chama.dto.PageResponse;
import com.calpex_technologies.chama.dto.ResourceDto;
import com.calpex_technologies.chama.security.UserPrincipal;
import com.calpex_technologies.chama.service.ResourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/resources")
@RequiredArgsConstructor
@Slf4j
public class ResourceController {

    private final ResourceService resourceService;

    /**
     * Get all resources
     */
    @GetMapping
    public ResponseEntity<List<ResourceDto>> getAllResources(@AuthenticationPrincipal UserPrincipal principal) {
        log.info("GET /api/resources - tenantId={}", principal.getTenantId());
        return ResponseEntity.ok(resourceService.getAllResources(principal.getTenantId()));
    }

    /**
     * Get paginated resources with search
     */
    @GetMapping("/paginated")
    public ResponseEntity<PageResponse<ResourceDto>> getResourcesPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("GET /api/resources/paginated - page={}, size={}, search={}", page, size, search);
        return ResponseEntity.ok(resourceService.getResourcesPaginated(principal.getTenantId(), page, size, search));
    }

    /**
     * Get single resource by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<ResourceDto> getResource(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(resourceService.getResource(id, principal.getTenantId()));
    }

    /**
     * Upload a new resource
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResourceDto> uploadResource(
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description,
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("POST /api/resources - uploading file: {}", file.getOriginalFilename());
        ResourceDto dto = resourceService.uploadResource(
                principal.getTenantId(),
                principal.getMemberId(),
                name,
                description,
                file
        );
        return ResponseEntity.ok(dto);
    }

    /**
     * Update resource metadata
     */
    @PutMapping("/{id}")
    public ResponseEntity<ResourceDto> updateResource(
            @PathVariable Long id,
            @RequestBody UpdateResourceRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("PUT /api/resources/{} - updating", id);
        ResourceDto dto = resourceService.updateResource(id, principal.getTenantId(), request.name(), request.description());
        return ResponseEntity.ok(dto);
    }

    /**
     * Delete resource (soft delete)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteResource(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("DELETE /api/resources/{}", id);
        resourceService.deleteResource(id, principal.getTenantId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Hard delete resource (removes file)
     */
    @DeleteMapping("/{id}/hard")
    public ResponseEntity<Void> hardDeleteResource(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("DELETE /api/resources/{}/hard", id);
        resourceService.hardDeleteResource(id, principal.getTenantId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Download resource file
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadResource(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("GET /api/resources/{}/download", id);
        
        Resource resource = resourceService.downloadResource(id, principal.getTenantId());
        String originalFileName = resourceService.getOriginalFileName(id, principal.getTenantId());
        String contentType = resourceService.getContentType(id, principal.getTenantId());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType != null ? contentType : "application/octet-stream"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + originalFileName + "\"")
                .body(resource);
    }

    // Request record
    record UpdateResourceRequest(String name, String description) {}
}
