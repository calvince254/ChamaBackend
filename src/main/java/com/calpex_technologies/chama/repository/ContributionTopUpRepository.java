package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.ContributionTopUp;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContributionTopUpRepository extends JpaRepository<ContributionTopUp, Long> {

    List<ContributionTopUp> findByTenantIdOrderByRequestDateDesc(Long tenantId);

    Page<ContributionTopUp> findByTenantIdOrderByRequestDateDesc(Long tenantId, Pageable pageable);

    Page<ContributionTopUp> findByTenantIdAndStatus(Long tenantId, ContributionTopUp.TopUpStatus status, Pageable pageable);

    List<ContributionTopUp> findByTenantIdAndMemberId(Long tenantId, Long memberId);

    Page<ContributionTopUp> findByTenantIdAndMemberId(Long tenantId, Long memberId, Pageable pageable);

    Optional<ContributionTopUp> findByIdAndTenantId(Long id, Long tenantId);

    @Query("SELECT t FROM ContributionTopUp t WHERE t.tenant.id = :tenantId " +
           "AND (:status IS NULL OR t.status = :status) " +
           "AND (:memberId IS NULL OR t.member.id = :memberId) " +
           "AND (:contributionTypeId IS NULL OR t.contributionType.id = :contributionTypeId) " +
           "ORDER BY t.requestDate DESC")
    Page<ContributionTopUp> findWithFilters(
            @Param("tenantId") Long tenantId,
            @Param("status") ContributionTopUp.TopUpStatus status,
            @Param("memberId") Long memberId,
            @Param("contributionTypeId") Long contributionTypeId,
            Pageable pageable);

    long countByTenantIdAndStatus(Long tenantId, ContributionTopUp.TopUpStatus status);
}
