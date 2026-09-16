package com.calpex_technologies.chama.service;

import com.calpex_technologies.chama.dto.PageResponse;
import com.calpex_technologies.chama.dto.ResourceDto;
import com.calpex_technologies.chama.entity.Member;
import com.calpex_technologies.chama.entity.Resource;
import com.calpex_technologies.chama.entity.Tenant;
import com.calpex_technologies.chama.repository.MemberRepository;
import com.calpex_technologies.chama.repository.ResourceRepository;
import com.calpex_technologies.chama.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ResourceService {

    private final ResourceRepository resourceRepository;
    private final TenantRepository tenantRepository;
    private final MemberRepository memberRepository;

    @Value("${app.upload.dir:uploads/resources}")
    private String uploadDir;

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * Get all active resources for a tenant
     */
    public List<ResourceDto> getAllResources(Long tenantId) {
        return resourceRepository.findByTenantIdAndActiveOrderByCreatedAtDesc(tenantId, true)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Get paginated resources
     */
    public PageResponse<ResourceDto> getResourcesPaginated(Long tenantId, int page, int size, String search) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Resource> resourcePage;

        if (search != null && !search.trim().isEmpty()) {
            resourcePage = resourceRepository.searchResources(tenantId, search.trim(), pageable);
        } else {
            resourcePage = resourceRepository.findByTenantIdAndActive(tenantId, true, pageable);
        }

        List<ResourceDto> content = resourcePage.getContent().stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return PageResponse.<ResourceDto>builder()
                .content(content)
                .page(resourcePage.getNumber())
                .size(resourcePage.getSize())
                .totalElements(resourcePage.getTotalElements())
                .totalPages(resourcePage.getTotalPages())
                .first(resourcePage.isFirst())
                .last(resourcePage.isLast())
                .build();
    }

    /**
     * Get a single resource by ID
     */
    public ResourceDto getResource(Long id, Long tenantId) {
        Resource resource = resourceRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Resource not found"));
        return toDto(resource);
    }

    /**
     * Upload a new resource
     */
    @Transactional
    public ResourceDto uploadResource(Long tenantId, Long uploadedByMemberId, String name, String description, MultipartFile file) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        Member uploadedBy = memberRepository.findById(uploadedByMemberId)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        // Create upload directory if it doesn't exist
        Path uploadPath = Paths.get(uploadDir, "tenant-" + tenantId);
        try {
            Files.createDirectories(uploadPath);
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory", e);
        }

        // Generate unique file name
        String originalFileName = StringUtils.cleanPath(file.getOriginalFilename());
        String fileExtension = getFileExtension(originalFileName);
        String uniqueFileName = UUID.randomUUID().toString() + "." + fileExtension;

        // Save file
        try {
            Path targetLocation = uploadPath.resolve(uniqueFileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            Resource resource = Resource.builder()
                    .tenant(tenant)
                    .name(name)
                    .description(description)
                    .fileName(uniqueFileName)
                    .originalFileName(originalFileName)
                    .filePath(targetLocation.toString())
                    .fileSize(file.getSize())
                    .contentType(file.getContentType())
                    .fileExtension(fileExtension)
                    .uploadedBy(uploadedBy)
                    .downloadCount(0)
                    .active(true)
                    .build();

            resource = resourceRepository.save(resource);
            log.info("Resource uploaded: {} by member {}", name, uploadedByMemberId);

            return toDto(resource);
        } catch (IOException e) {
            throw new RuntimeException("Could not store file", e);
        }
    }

    /**
     * Update resource metadata (not the file itself)
     */
    @Transactional
    public ResourceDto updateResource(Long id, Long tenantId, String name, String description) {
        Resource resource = resourceRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Resource not found"));

        if (name != null && !name.isEmpty()) {
            resource.setName(name);
        }
        if (description != null) {
            resource.setDescription(description);
        }

        resource = resourceRepository.save(resource);
        log.info("Resource updated: {}", id);

        return toDto(resource);
    }

    /**
     * Delete resource (soft delete)
     */
    @Transactional
    public void deleteResource(Long id, Long tenantId) {
        Resource resource = resourceRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Resource not found"));

        resource.setActive(false);
        resourceRepository.save(resource);
        log.info("Resource deleted (soft): {}", id);
    }

    /**
     * Hard delete resource (removes file from disk)
     */
    @Transactional
    public void hardDeleteResource(Long id, Long tenantId) {
        Resource resource = resourceRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Resource not found"));

        // Delete file from disk
        try {
            Path filePath = Paths.get(resource.getFilePath());
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.warn("Could not delete file: {}", resource.getFilePath(), e);
        }

        resourceRepository.delete(resource);
        log.info("Resource hard deleted: {}", id);
    }

    /**
     * Download resource - returns the file as UrlResource
     */
    @Transactional
    public org.springframework.core.io.Resource downloadResource(Long id, Long tenantId) {
        Resource resource = resourceRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Resource not found"));

        if (!resource.getActive()) {
            throw new RuntimeException("Resource is not available");
        }

        try {
            Path filePath = Paths.get(resource.getFilePath());
            org.springframework.core.io.Resource fileResource = new UrlResource(filePath.toUri());

            if (fileResource.exists() && fileResource.isReadable()) {
                // Increment download count
                resource.setDownloadCount(resource.getDownloadCount() + 1);
                resourceRepository.save(resource);

                return fileResource;
            } else {
                throw new RuntimeException("File not found or not readable");
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("File not found", e);
        }
    }

    /**
     * Get original file name for download headers
     */
    public String getOriginalFileName(Long id, Long tenantId) {
        Resource resource = resourceRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Resource not found"));
        return resource.getOriginalFileName();
    }

    /**
     * Get content type for download headers
     */
    public String getContentType(Long id, Long tenantId) {
        Resource resource = resourceRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Resource not found"));
        return resource.getContentType();
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.lastIndexOf('.') == -1) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    private ResourceDto toDto(Resource resource) {
        return ResourceDto.builder()
                .id(resource.getId())
                .name(resource.getName())
                .description(resource.getDescription())
                .fileName(resource.getFileName())
                .originalFileName(resource.getOriginalFileName())
                .fileSize(resource.getFileSize())
                .contentType(resource.getContentType())
                .fileExtension(resource.getFileExtension())
                .uploadedById(resource.getUploadedBy() != null ? resource.getUploadedBy().getId() : null)
                .uploadedByName(resource.getUploadedBy() != null ? resource.getUploadedBy().getName() : null)
                .downloadCount(resource.getDownloadCount())
                .active(resource.getActive())
                .createdAt(resource.getCreatedAt() != null ? resource.getCreatedAt().format(DATETIME_FORMATTER) : null)
                .updatedAt(resource.getUpdatedAt() != null ? resource.getUpdatedAt().format(DATETIME_FORMATTER) : null)
                .build();
    }
}
