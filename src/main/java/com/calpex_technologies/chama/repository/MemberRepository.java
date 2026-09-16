package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.Member;
import com.calpex_technologies.chama.entity.Tenant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {
    List<Member> findByTenant(Tenant tenant);
    List<Member> findByTenantId(Long tenantId);
    Optional<Member> findByEmailAndTenant(String email, Tenant tenant);
    Optional<Member> findByEmail(String email);
    List<Member> findAllByEmail(String email);
    Optional<Member> findByEmailAndTenantId(String email, Long tenantId);
    Optional<Member> findByPhoneAndTenant(String phone, Tenant tenant);
    Optional<Member> findByIdAndTenantId(Long id, Long tenantId);
    boolean existsByEmailAndTenant(String email, Tenant tenant);
    long countByTenantId(Long tenantId);
    
    @Query("SELECT m FROM Member m LEFT JOIN FETCH m.role WHERE m.id = :id")
    Optional<Member> findByIdWithRole(Long id);
    
    @Query("SELECT m FROM Member m LEFT JOIN FETCH m.role r LEFT JOIN FETCH r.rolePermissions WHERE m.id = :id")
    Optional<Member> findByIdWithRoleAndPermissions(Long id);
    
    @Query("SELECT m FROM Member m LEFT JOIN FETCH m.role WHERE m.tenant.id = :tenantId")
    List<Member> findByTenantIdWithRole(Long tenantId);
    
    @Query("SELECT m FROM Member m WHERE m.tenant.id = :tenantId AND m.status = 'ACTIVE'")
    List<Member> findActiveByTenantId(Long tenantId);
    
    // Find by tenant and status
    List<Member> findByTenantIdAndStatus(Long tenantId, Member.MemberStatus status);

    // Find all tenants a member belongs to (by email)
    @Query("SELECT m.tenant FROM Member m WHERE m.email = :email AND m.tenant IS NOT NULL")
    List<Tenant> findTenantsByEmail(String email);

    @Modifying
    void deleteByTenant(Tenant tenant);
    
    // Paginated queries
    @Query("SELECT m FROM Member m LEFT JOIN FETCH m.role WHERE m.tenant.id = :tenantId")
    Page<Member> findByTenantIdWithRole(@Param("tenantId") Long tenantId, Pageable pageable);
    
    @Query("SELECT m FROM Member m LEFT JOIN FETCH m.role WHERE m.tenant.id = :tenantId AND " +
           "(LOWER(m.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(m.memberNo) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Member> findByTenantIdWithRoleAndSearch(@Param("tenantId") Long tenantId, 
                                                  @Param("search") String search, 
                                                  Pageable pageable);
    
    @Query("SELECT m FROM Member m LEFT JOIN FETCH m.role WHERE m.tenant.id = :tenantId AND m.status = :status")
    Page<Member> findByTenantIdWithRoleAndStatus(@Param("tenantId") Long tenantId, 
                                                  @Param("status") Member.MemberStatus status, 
                                                  Pageable pageable);
    
    @Query("SELECT m FROM Member m LEFT JOIN FETCH m.role WHERE m.tenant.id = :tenantId AND " +
           "m.status = :status AND " +
           "(LOWER(m.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(m.memberNo) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Member> findByTenantIdWithRoleStatusAndSearch(@Param("tenantId") Long tenantId, 
                                                        @Param("status") Member.MemberStatus status,
                                                        @Param("search") String search, 
                                                        Pageable pageable);
}
