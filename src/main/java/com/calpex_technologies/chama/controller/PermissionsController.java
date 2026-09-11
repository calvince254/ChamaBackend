package com.calpex_technologies.chama.controller;

import com.calpex_technologies.chama.security.HttpConfigurer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
public class PermissionsController {

    /**
     * Get all available permissions grouped by module.
     * This endpoint is used by the Roles & Rights UI to show all available permissions.
     */
    @GetMapping
    public ResponseEntity<List<ModulePermissions>> getAllPermissions() {
        Map<String, List<HttpConfigurer.PermissionDefinition>> allPermissions = HttpConfigurer.getAllPermissions();
        
        List<ModulePermissions> result = allPermissions.entrySet().stream()
                .map(entry -> new ModulePermissions(
                        entry.getKey(),
                        entry.getValue().stream()
                                .map(p -> new PermissionItem(p.code(), p.displayName()))
                                .collect(Collectors.toList())
                ))
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * DTO for module with its permissions
     */
    public record ModulePermissions(String module, List<PermissionItem> permissions) {}
    
    /**
     * DTO for individual permission
     */
    public record PermissionItem(String code, String displayName) {}
}
