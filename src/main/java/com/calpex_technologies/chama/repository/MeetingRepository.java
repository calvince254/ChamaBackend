package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.Meeting;
import com.calpex_technologies.chama.entity.Tenant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MeetingRepository extends JpaRepository<Meeting, Long> {
    List<Meeting> findByTenantIdOrderByDateDesc(Long tenantId);
    Optional<Meeting> findByIdAndTenantId(Long id, Long tenantId);
    List<Meeting> findByTenantIdAndDateAfterOrderByDateAsc(Long tenantId, LocalDate date);
    
    @Query("SELECT m FROM Meeting m WHERE m.tenant.id = :tenantId AND m.date >= :today ORDER BY m.date ASC LIMIT 1")
    Optional<Meeting> findNextMeeting(@Param("tenantId") Long tenantId, @Param("today") LocalDate today);

    @Modifying
    void deleteByTenant(Tenant tenant);
    
    // Paginated queries
    Page<Meeting> findByTenantIdOrderByDateDesc(Long tenantId, Pageable pageable);
    
    @Query("SELECT m FROM Meeting m WHERE m.tenant.id = :tenantId AND " +
           "(LOWER(m.venue) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(m.type) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Meeting> findByTenantIdAndSearch(@Param("tenantId") Long tenantId, 
                                          @Param("search") String search, 
                                          Pageable pageable);
}
