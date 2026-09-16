package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.License;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@Repository
public interface LicenseRepository extends JpaRepository<License, Long> {

    Optional<License> findByTenantId(Long tenantId);

    boolean existsByTenantId(Long tenantId);

    List<License> findByStatus(License.LicenseStatus status);

    @Query("SELECT l FROM License l WHERE l.billingDayOfMonth = :billingDay AND l.status = :status")
    List<License> findByBillingDayOfMonthAndStatus(
            @Param("billingDay") Integer billingDay,
            @Param("status") License.LicenseStatus status);

    @Query("SELECT l FROM License l WHERE l.status = 'ACTIVE'")
    List<License> findAllActive();

    @Query("SELECT l FROM License l WHERE l.status = 'SUSPENDED'")
    List<License> findAllSuspended();

    List<License> findByStatusIn(List<License.LicenseStatus> statuses);

    @Query("SELECT l FROM License l JOIN FETCH l.tenant")
    List<License> findAllWithTenant();
}
