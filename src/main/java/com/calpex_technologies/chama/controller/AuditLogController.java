package com.calpex_technologies.chama.controller;

import com.calpex_technologies.chama.dto.AuditLogDto;
import com.calpex_technologies.chama.dto.PageResponse;
import com.calpex_technologies.chama.security.UserPrincipal;
import com.calpex_technologies.chama.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
@Slf4j
public class AuditLogController {

    private final AuditLogService auditLogService;

    /**
     * Get paginated audit logs with filters
     */
    @GetMapping
    public ResponseEntity<PageResponse<AuditLogDto>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("GET /api/audit-logs - page={}, size={}, module={}, memberId={}", page, size, module, memberId);
        
        PageResponse<AuditLogDto> response = auditLogService.getAuditLogs(
                principal.getTenantId(), page, size, module, memberId, action, startDate, endDate);
        return ResponseEntity.ok(response);
    }

    /**
     * Get single audit log entry
     */
    @GetMapping("/{id}")
    public ResponseEntity<AuditLogDto> getAuditLog(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(auditLogService.getAuditLog(id));
    }

    /**
     * Get distinct modules for filter dropdown
     */
    @GetMapping("/modules")
    public ResponseEntity<List<String>> getModules(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(auditLogService.getDistinctModules(principal.getTenantId()));
    }

    /**
     * Get distinct actions for filter dropdown
     */
    @GetMapping("/actions")
    public ResponseEntity<List<String>> getActions(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(auditLogService.getDistinctActions(principal.getTenantId()));
    }

    /**
     * Get audit statistics for dashboard
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics(
            @RequestParam(defaultValue = "7") int days,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(auditLogService.getAuditStatistics(principal.getTenantId(), days));
    }

    /**
     * Get all audit logs (super admin only - all tenants)
     */
    @GetMapping("/all")
    public ResponseEntity<PageResponse<AuditLogDto>> getAllAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (!principal.isSuperAdmin()) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(auditLogService.getAllAuditLogs(page, size));
    }

    /**
     * Delete old audit logs (super admin maintenance)
     */
    @DeleteMapping("/cleanup")
    public ResponseEntity<Map<String, Object>> cleanupOldLogs(
            @RequestParam(defaultValue = "90") int daysToKeep,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (!principal.isSuperAdmin()) {
            return ResponseEntity.status(403).build();
        }
        long deleted = auditLogService.deleteOldLogs(daysToKeep);
        return ResponseEntity.ok(Map.of("deleted", deleted, "message", "Deleted " + deleted + " old audit logs"));
    }
}
