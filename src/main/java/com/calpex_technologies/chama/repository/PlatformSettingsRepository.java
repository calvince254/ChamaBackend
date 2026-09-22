package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.PlatformSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlatformSettingsRepository extends JpaRepository<PlatformSettings, Long> {
    
    Optional<PlatformSettings> findByKey(String key);
}
