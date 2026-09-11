package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {
    
    List<RolePermission> findByRoleId(Long roleId);
    
    void deleteByRoleId(Long roleId);
}
