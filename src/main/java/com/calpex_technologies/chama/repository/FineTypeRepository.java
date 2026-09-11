package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.FineType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FineTypeRepository extends JpaRepository<FineType, Long> {
    
    List<FineType> findByTenantIdOrderByNameAsc(Long tenantId);
    
    List<FineType> findByTenantIdAndActiveTrue(Long tenantId);
    
    Optional<FineType> findByIdAndTenantId(Long id, Long tenantId);
    
    boolean existsByTenantIdAndNameIgnoreCase(Long tenantId, String name);
    
    // Find automatic fine types by trigger
    Optional<FineType> findByTenantIdAndActiveTrueAndAutomaticTrueAndTriggerType(
            Long tenantId, FineType.TriggerType triggerType);
    
    List<FineType> findByTenantIdAndAutomaticTrue(Long tenantId);
}
