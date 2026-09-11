package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.Role;
import com.calpex_technologies.chama.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {
    List<Role> findByTenantId(Long tenantId);
    List<Role> findByTenant(Tenant tenant);
    Optional<Role> findByNameAndTenantId(String name, Long tenantId);
    Optional<Role> findByIdAndTenantId(Long id, Long tenantId);

    @Modifying
    void deleteByTenant(Tenant tenant);
}
