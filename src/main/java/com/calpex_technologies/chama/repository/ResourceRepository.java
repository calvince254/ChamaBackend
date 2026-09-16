package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResourceRepository extends JpaRepository<Resource, Long> {

    List<Resource> findByTenantIdAndActiveOrderByCreatedAtDesc(Long tenantId, Boolean active);

    Page<Resource> findByTenantIdAndActive(Long tenantId, Boolean active, Pageable pageable);

    @Query("SELECT r FROM Resource r WHERE r.tenant.id = :tenantId AND r.active = true " +
           "AND (LOWER(r.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(r.description) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Resource> searchResources(Long tenantId, String search, Pageable pageable);

    Optional<Resource> findByIdAndTenantId(Long id, Long tenantId);

    long countByTenantIdAndActive(Long tenantId, Boolean active);
}
