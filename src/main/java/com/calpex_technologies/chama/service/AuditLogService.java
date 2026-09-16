package com.calpex_technologies.chama.service;

import com.calpex_technologies.chama.dto.AuditLogDto;
import com.calpex_technologies.chama.dto.PageResponse;
import com.calpex_technologies.chama.entity.AuditLog;
import com.calpex_technologies.chama.entity.Member;
import com.calpex_technologies.chama.entity.Tenant;
import com.calpex_technologies.chama.repository.AuditLogRepository;
import com.calpex_technologies.chama.repository.MemberRepository;
import com.calpex_technologies.chama.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final TenantRepository tenantRepository;
    private final MemberRepository memberRepository;

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Log an audit event asynchronously
     */
    @Async
    @Transactional
    public void logAsync(Long tenantId, Long memberId, String module, String action, String httpMethod,
                         String endpoint, String entityType, Long entityId, String description,
                         String oldValue, String newValue, String ipAddress, String userAgent,
                         Integer responseStatus, Long executionTimeMs, boolean success, String errorMessage) {
        try {
            logSync(tenantId, memberId, module, action, httpMethod, endpoint, entityType, entityId,
                    description, oldValue, newValue, ipAddress, userAgent, responseStatus, executionTimeMs, success, errorMessage);
        } catch (Exception e) {
            log.error("Failed to log audit event asynchronously", e);
        }
    }

    /**
     * Log an audit event synchronously
     */
    @Transactional
    public void logSync(Long tenantId, Long memberId, String module, String action, String httpMethod,
                        String endpoint, String entityType, Long entityId, String description,
                        String oldValue, String newValue, String ipAddress, String userAgent,
                        Integer responseStatus, Long executionTimeMs, boolean success, String errorMessage) {
        
        Tenant tenant = tenantId != null ? tenantRepository.findById(tenantId).orElse(null) : null;
        Member member = memberId != null ? memberRepository.findById(memberId).orElse(null) : null;

        AuditLog auditLog = AuditLog.builder()
                .tenant(tenant)
                .member(member)
                .memberName(member != null ? member.getName() : null)
                .memberEmail(member != null ? member.getEmail() : null)
                .module(module)
                .action(action)
                .httpMethod(httpMethod)
                .endpoint(endpoint)
                .entityType(entityType)
                .entityId(entityId)
                .description(description)
                .oldValue(truncate(oldValue, 65535))
                .newValue(truncate(newValue, 65535))
                .ipAddress(ipAddress)
                .userAgent(truncate(userAgent, 500))
                .timestamp(LocalDateTime.now())
                .responseStatus(responseStatus)
                .executionTimeMs(executionTimeMs)
                .status(success ? AuditLog.AuditStatus.SUCCESS : AuditLog.AuditStatus.FAILURE)
                .errorMessage(truncate(errorMessage, 1000))
                .build();

        auditLogRepository.save(auditLog);
    }

    /**
     * Get paginated audit logs with filters
     */
    public PageResponse<AuditLogDto> getAuditLogs(Long tenantId, int page, int size,
                                                   String module, Long memberId, String action,
                                                   LocalDate startDate, LocalDate endDate) {
        Pageable pageable = PageRequest.of(page, size);
        
        LocalDateTime startDateTime = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime endDateTime = endDate != null ? endDate.atTime(23, 59, 59) : null;

        Page<AuditLog> auditPage = auditLogRepository.searchAuditLogs(
                tenantId, module, memberId, action, startDateTime, endDateTime, pageable);

        List<AuditLogDto> content = auditPage.getContent().stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return PageResponse.<AuditLogDto>builder()
                .content(content)
                .page(auditPage.getNumber())
                .size(auditPage.getSize())
                .totalElements(auditPage.getTotalElements())
                .totalPages(auditPage.getTotalPages())
                .first(auditPage.isFirst())
                .last(auditPage.isLast())
                .build();
    }

    /**
     * Get single audit log entry
     */
    public AuditLogDto getAuditLog(Long id) {
        AuditLog auditLog = auditLogRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Audit log not found"));
        return toDto(auditLog);
    }

    /**
     * Get distinct modules for filter dropdown
     */
    public List<String> getDistinctModules(Long tenantId) {
        return auditLogRepository.findDistinctModules(tenantId);
    }

    /**
     * Get distinct actions for filter dropdown
     */
    public List<String> getDistinctActions(Long tenantId) {
        return auditLogRepository.findDistinctActions(tenantId);
    }

    /**
     * Get audit statistics for dashboard
     */
    public Map<String, Object> getAuditStatistics(Long tenantId, int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalActivities", auditLogRepository.countActivitiesSince(tenantId, since));
        
        // Activities by module
        List<Object[]> byModule = auditLogRepository.countByModuleSince(tenantId, since);
        Map<String, Long> moduleStats = new HashMap<>();
        for (Object[] row : byModule) {
            moduleStats.put((String) row[0], (Long) row[1]);
        }
        stats.put("byModule", moduleStats);
        
        // Activities by member
        List<Object[]> byMember = auditLogRepository.countByMemberSince(tenantId, since);
        Map<String, Long> memberStats = new HashMap<>();
        int count = 0;
        for (Object[] row : byMember) {
            if (count++ < 10) { // Top 10 members
                memberStats.put((String) row[0], (Long) row[1]);
            }
        }
        stats.put("byMember", memberStats);
        
        return stats;
    }

    /**
     * Get all audit logs for super admin (all tenants)
     */
    public PageResponse<AuditLogDto> getAllAuditLogs(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<AuditLog> auditPage = auditLogRepository.findAllByOrderByTimestampDesc(pageable);

        List<AuditLogDto> content = auditPage.getContent().stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return PageResponse.<AuditLogDto>builder()
                .content(content)
                .page(auditPage.getNumber())
                .size(auditPage.getSize())
                .totalElements(auditPage.getTotalElements())
                .totalPages(auditPage.getTotalPages())
                .first(auditPage.isFirst())
                .last(auditPage.isLast())
                .build();
    }

    /**
     * Delete old audit logs (for maintenance)
     */
    @Transactional
    public long deleteOldLogs(int daysToKeep) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(daysToKeep);
        long countBefore = auditLogRepository.count();
        auditLogRepository.deleteByTimestampBefore(cutoff);
        long countAfter = auditLogRepository.count();
        long deleted = countBefore - countAfter;
        log.info("Deleted {} old audit logs (older than {} days)", deleted, daysToKeep);
        return deleted;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    private AuditLogDto toDto(AuditLog log) {
        return AuditLogDto.builder()
                .id(log.getId())
                .tenantId(log.getTenant() != null ? log.getTenant().getId() : null)
                .tenantName(log.getTenant() != null ? log.getTenant().getName() : null)
                .memberId(log.getMember() != null ? log.getMember().getId() : null)
                .memberName(log.getMemberName())
                .memberEmail(log.getMemberEmail())
                .module(log.getModule())
                .action(log.getAction())
                .httpMethod(log.getHttpMethod())
                .endpoint(log.getEndpoint())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .description(log.getDescription())
                .oldValue(log.getOldValue())
                .newValue(log.getNewValue())
                .ipAddress(log.getIpAddress())
                .userAgent(log.getUserAgent())
                .timestamp(log.getTimestamp() != null ? log.getTimestamp().format(DATETIME_FORMATTER) : null)
                .responseStatus(log.getResponseStatus())
                .executionTimeMs(log.getExecutionTimeMs())
                .status(log.getStatus() != null ? log.getStatus().name() : null)
                .errorMessage(log.getErrorMessage())
                .build();
    }
}
