package com.calpex_technologies.chama.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileStorageService {

    @Value("${app.file.upload-dir:./uploads}")
    private String uploadDir;

    private Path fileStorageLocation;

    @PostConstruct
    public void init() {
        this.fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (IOException ex) {
            throw new RuntimeException("Could not create upload directory", ex);
        }
    }

    /**
     * Store a file and return the stored file path (relative to upload dir)
     * 
     * @param file The file to store
     * @param subDir Subdirectory (e.g., "meeting-minutes", "documents")
     * @param tenantId Tenant ID for organization
     * @return The relative path where file was stored
     */
    public String storeFile(MultipartFile file, String subDir, Long tenantId) {
        // Normalize file name
        String originalFileName = StringUtils.cleanPath(file.getOriginalFilename());
        
        // Check for invalid characters
        if (originalFileName.contains("..")) {
            throw new RuntimeException("Invalid file name: " + originalFileName);
        }
        
        // Generate unique filename to prevent overwriting
        String fileExtension = "";
        int dotIndex = originalFileName.lastIndexOf('.');
        if (dotIndex > 0) {
            fileExtension = originalFileName.substring(dotIndex);
        }
        String uniqueFileName = UUID.randomUUID().toString() + fileExtension;
        
        // Create subdirectory path: uploads/{subDir}/{tenantId}/
        Path targetDir = this.fileStorageLocation.resolve(subDir).resolve(tenantId.toString());
        try {
            Files.createDirectories(targetDir);
        } catch (IOException ex) {
            throw new RuntimeException("Could not create directory: " + targetDir, ex);
        }
        
        // Store file
        Path targetLocation = targetDir.resolve(uniqueFileName);
        try {
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new RuntimeException("Could not store file: " + originalFileName, ex);
        }
        
        // Return relative path from upload dir
        return subDir + "/" + tenantId + "/" + uniqueFileName;
    }

    /**
     * Store bytes directly as a file
     * 
     * @param bytes File content as bytes
     * @param fileName Original file name (used for extension)
     * @param subDir Subdirectory (e.g., "meeting-minutes", "documents")
     * @param tenantId Tenant ID for organization
     * @return The relative path where file was stored
     */
    public String storeBytes(byte[] bytes, String fileName, String subDir, Long tenantId) {
        // Normalize file name
        String cleanFileName = StringUtils.cleanPath(fileName);
        
        // Check for invalid characters
        if (cleanFileName.contains("..")) {
            throw new RuntimeException("Invalid file name: " + cleanFileName);
        }
        
        // Generate unique filename
        String fileExtension = "";
        int dotIndex = cleanFileName.lastIndexOf('.');
        if (dotIndex > 0) {
            fileExtension = cleanFileName.substring(dotIndex);
        }
        String uniqueFileName = UUID.randomUUID().toString() + fileExtension;
        
        // Create subdirectory path: uploads/{subDir}/{tenantId}/
        Path targetDir = this.fileStorageLocation.resolve(subDir).resolve(tenantId.toString());
        try {
            Files.createDirectories(targetDir);
        } catch (IOException ex) {
            throw new RuntimeException("Could not create directory: " + targetDir, ex);
        }
        
        // Store file
        Path targetLocation = targetDir.resolve(uniqueFileName);
        try {
            Files.write(targetLocation, bytes);
        } catch (IOException ex) {
            throw new RuntimeException("Could not store file: " + cleanFileName, ex);
        }
        
        // Return relative path from upload dir
        return subDir + "/" + tenantId + "/" + uniqueFileName;
    }

    /**
     * Load a file as a Resource
     * 
     * @param filePath The relative file path (as returned by storeFile)
     * @return Resource for downloading
     */
    public Resource loadFileAsResource(String filePath) {
        try {
            Path file = this.fileStorageLocation.resolve(filePath).normalize();
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new RuntimeException("File not found: " + filePath);
            }
        } catch (MalformedURLException ex) {
            throw new RuntimeException("File not found: " + filePath, ex);
        }
    }

    /**
     * Delete a file
     * 
     * @param filePath The relative file path
     */
    public void deleteFile(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return;
        }
        try {
            Path file = this.fileStorageLocation.resolve(filePath).normalize();
            Files.deleteIfExists(file);
        } catch (IOException ex) {
            // Log but don't throw - deletion failure shouldn't break other operations
            System.err.println("Could not delete file: " + filePath);
        }
    }

    /**
     * Get the content type of a file based on extension
     */
    public String getContentType(String fileName) {
        String extension = "";
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = fileName.substring(dotIndex + 1).toLowerCase();
        }
        
        return switch (extension) {
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "txt" -> "text/plain";
            default -> "application/octet-stream";
        };
    }
}
