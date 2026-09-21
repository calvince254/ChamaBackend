package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.Payable;
import com.calpex_technologies.chama.entity.PayableStatus;
import com.calpex_technologies.chama.entity.PayableType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PayableRepository extends JpaRepository<Payable, Long> {
    
    Optional<Payable> findByIdAndTenant_Id(Long id, Long tenantId);
    
    /**
     * Find payable by type and reference ID (the ID in the module-specific table).
     */
    Optional<Payable> findByPayableTypeAndPayableRefIdAndTenant_Id(
            PayableType payableType, Long payableRefId, Long tenantId);
    
    /**
     * Find all outstanding payables for a member.
     */
    List<Payable> findByMember_IdAndStatusOrderByDueDateAsc(Long memberId, PayableStatus status);
    
    /**
     * Find all payables for a member.
     */
    List<Payable> findByMember_IdAndTenant_IdOrderByCreatedAtDesc(Long memberId, Long tenantId);
    
    /**
     * Find all payables for a member with a specific status.
     */
    List<Payable> findByMember_IdAndTenant_IdAndStatusOrderByCreatedAtDesc(
            Long memberId, Long tenantId, PayableStatus status);
    
    /**
     * Find all payables for a tenant with pagination.
     */
    Page<Payable> findByTenant_IdOrderByCreatedAtDesc(Long tenantId, Pageable pageable);
    
    /**
     * Find payables awaiting confirmation for a tenant.
     */
    List<Payable> findByTenant_IdAndStatusOrderByUpdatedAtDesc(Long tenantId, PayableStatus status);
    
    /**
     * Count outstanding payables for a member.
     */
    long countByMember_IdAndStatus(Long memberId, PayableStatus status);
    
    /**
     * Count awaiting confirmation payables for a tenant.
     */
    long countByTenant_IdAndStatus(Long tenantId, PayableStatus status);
    
    /**
     * Find payables by type for a tenant.
     */
    @Query("SELECT p FROM Payable p WHERE p.tenant.id = :tenantId AND p.payableType = :payableType " +
           "ORDER BY p.createdAt DESC")
    Page<Payable> findByTenantIdAndPayableType(
            @Param("tenantId") Long tenantId,
            @Param("payableType") PayableType payableType,
            Pageable pageable);
    
    /**
     * Find outstanding payables by type for a member.
     */
    @Query("SELECT p FROM Payable p WHERE p.member.id = :memberId AND p.payableType = :payableType " +
           "AND p.status = 'OUTSTANDING' ORDER BY p.dueDate ASC")
    List<Payable> findOutstandingByMemberIdAndType(
            @Param("memberId") Long memberId,
            @Param("payableType") PayableType payableType);
    
    /**
     * Find payable by type and reference ID (without tenant - for checking pending claims).
     */
    Optional<Payable> findByPayableTypeAndPayableRefId(PayableType payableType, Long payableRefId);
    
    /**
     * Find all reference IDs that have pending payment claims (AWAITING_CONFIRMATION status).
     * This is optimized for batch lookups to avoid N+1 queries.
     */
    @Query("SELECT p.payableRefId FROM Payable p " +
           "WHERE p.tenant.id = :tenantId " +
           "AND p.payableType = :payableType " +
           "AND p.payableRefId IN :refIds " +
           "AND p.status = 'AWAITING_CONFIRMATION'")
    List<Long> findRefIdsWithPendingClaims(
            @Param("tenantId") Long tenantId,
            @Param("payableType") PayableType payableType,
            @Param("refIds") List<Long> refIds);
    
    /**
     * Find payables with AWAITING_CONFIRMATION status for batch lookup.
     * Returns full Payable entities so we can extract payableId for confirmation.
     */
    List<Payable> findByTenant_IdAndPayableTypeAndPayableRefIdInAndStatus(
            Long tenantId, PayableType payableType, List<Long> refIds, PayableStatus status);
    
    /**
     * Find payable by type and reference ID with status in a list.
     * Used for loan repayments to find existing OUTSTANDING or AWAITING_CONFIRMATION payables.
     */
    Optional<Payable> findByPayableTypeAndPayableRefIdAndTenant_IdAndStatusIn(
            PayableType payableType, Long payableRefId, Long tenantId, List<PayableStatus> statuses);

    /**
     * Find all payables for a tenant by type and status.
     * Used for batch lookups (e.g., all AWAITING_CONFIRMATION registration fees).
     */
    List<Payable> findByTenant_IdAndPayableTypeAndStatus(
            Long tenantId, PayableType payableType, PayableStatus status);
}
