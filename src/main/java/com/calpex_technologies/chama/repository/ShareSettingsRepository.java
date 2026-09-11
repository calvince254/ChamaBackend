package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.ShareSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ShareSettingsRepository extends JpaRepository<ShareSettings, Long> {

    Optional<ShareSettings> findByTenantId(Long tenantId);

    @Modifying
    @Query("DELETE FROM ShareSettings ss WHERE ss.tenant.id = :tenantId")
    void deleteByTenantId(Long tenantId);
}
