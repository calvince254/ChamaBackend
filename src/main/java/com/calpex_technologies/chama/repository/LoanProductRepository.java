package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.LoanProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoanProductRepository extends JpaRepository<LoanProduct, Long> {
    
    List<LoanProduct> findByTenantIdOrderByNameAsc(Long tenantId);
    
    List<LoanProduct> findByTenantIdAndActiveTrue(Long tenantId);
    
    Optional<LoanProduct> findByIdAndTenantId(Long id, Long tenantId);
    
    boolean existsByTenantIdAndNameIgnoreCase(Long tenantId, String name);
    
    boolean existsByTenantIdAndNameIgnoreCaseAndIdNot(Long tenantId, String name, Long id);
}
