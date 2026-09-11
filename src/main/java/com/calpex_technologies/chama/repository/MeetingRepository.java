package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.Meeting;
import com.calpex_technologies.chama.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MeetingRepository extends JpaRepository<Meeting, Long> {
    List<Meeting> findByTenantIdOrderByDateDesc(Long tenantId);
    Optional<Meeting> findByIdAndTenantId(Long id, Long tenantId);
    List<Meeting> findByTenantIdAndDateAfterOrderByDateAsc(Long tenantId, LocalDate date);

    @Modifying
    void deleteByTenant(Tenant tenant);
}
