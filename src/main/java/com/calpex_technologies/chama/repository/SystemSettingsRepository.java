package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.SystemSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SystemSettingsRepository extends JpaRepository<SystemSettings, Long> {

    /**
     * Get the singleton system settings record
     */
    @Query("SELECT s FROM SystemSettings s ORDER BY s.id ASC LIMIT 1")
    Optional<SystemSettings> findFirst();
}
