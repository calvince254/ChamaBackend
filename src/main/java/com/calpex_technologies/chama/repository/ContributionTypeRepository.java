package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.ContributionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContributionTypeRepository extends JpaRepository<ContributionType, Long> {
    
    List<ContributionType> findByTenantIdOrderByNameAsc(Long tenantId);
    
    List<ContributionType> findByTenantIdAndActiveTrue(Long tenantId);
    
    Optional<ContributionType> findByIdAndTenantId(Long id, Long tenantId);
    
    boolean existsByTenantIdAndNameIgnoreCase(Long tenantId, String name);
    
    boolean existsByTenantIdAndNameIgnoreCaseAndIdNot(Long tenantId, String name, Long id);
}
