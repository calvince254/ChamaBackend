package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByTenantIdOrderByTimestampDesc(Long tenantId, Pageable pageable);

    Page<AuditLog> findByTenantIdAndModuleOrderByTimestampDesc(Long tenantId, String module, Pageable pageable);

    Page<AuditLog> findByTenantIdAndMemberIdOrderByTimestampDesc(Long tenantId, Long memberId, Pageable pageable);

    @Query("SELECT a FROM AuditLog a WHERE a.tenant.id = :tenantId " +
           "AND (:module IS NULL OR a.module = :module) " +
           "AND (:memberId IS NULL OR a.member.id = :memberId) " +
           "AND (:action IS NULL OR a.action = :action) " +
           "AND (:startDate IS NULL OR a.timestamp >= :startDate) " +
           "AND (:endDate IS NULL OR a.timestamp <= :endDate) " +
           "ORDER BY a.timestamp DESC")
    Page<AuditLog> searchAuditLogs(
            Long tenantId,
            String module,
            Long memberId,
            String action,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable
    );

    @Query("SELECT DISTINCT a.module FROM AuditLog a WHERE a.tenant.id = :tenantId ORDER BY a.module")
    List<String> findDistinctModules(Long tenantId);

    @Query("SELECT DISTINCT a.action FROM AuditLog a WHERE a.tenant.id = :tenantId ORDER BY a.action")
    List<String> findDistinctActions(Long tenantId);

    // Statistics
    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.tenant.id = :tenantId AND a.timestamp >= :since")
    long countActivitiesSince(Long tenantId, LocalDateTime since);

    @Query("SELECT a.module, COUNT(a) FROM AuditLog a WHERE a.tenant.id = :tenantId AND a.timestamp >= :since GROUP BY a.module ORDER BY COUNT(a) DESC")
    List<Object[]> countByModuleSince(Long tenantId, LocalDateTime since);

    @Query("SELECT a.member.name, COUNT(a) FROM AuditLog a WHERE a.tenant.id = :tenantId AND a.timestamp >= :since AND a.member IS NOT NULL GROUP BY a.member.name ORDER BY COUNT(a) DESC")
    List<Object[]> countByMemberSince(Long tenantId, LocalDateTime since);

    // For super admin - all tenants
    Page<AuditLog> findAllByOrderByTimestampDesc(Pageable pageable);

    // Delete old logs (for cleanup)
    void deleteByTimestampBefore(LocalDateTime before);
}
