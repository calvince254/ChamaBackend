package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.Permission;
import com.calpex_technologies.chama.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long> {
    List<Permission> findByRoleId(Long roleId);
    void deleteByRoleId(Long roleId);

    @Modifying
    void deleteByRole(Role role);
}
