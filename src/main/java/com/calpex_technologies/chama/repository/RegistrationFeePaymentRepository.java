package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.RegistrationFeePayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RegistrationFeePaymentRepository extends JpaRepository<RegistrationFeePayment, Long> {

    Optional<RegistrationFeePayment> findByTenantIdAndMemberId(Long tenantId, Long memberId);

    List<RegistrationFeePayment> findByTenantIdOrderByCreatedAtDesc(Long tenantId);

    List<RegistrationFeePayment> findByTenantIdAndStatus(Long tenantId, RegistrationFeePayment.PaymentStatus status);

    @Query("SELECT r FROM RegistrationFeePayment r WHERE r.tenant.id = :tenantId AND r.status = 'AWAITING_VERIFICATION' ORDER BY r.paidAt ASC")
    List<RegistrationFeePayment> findAwaitingVerification(Long tenantId);

    boolean existsByTenantIdAndMemberIdAndStatus(Long tenantId, Long memberId, RegistrationFeePayment.PaymentStatus status);

    @Query("SELECT COUNT(r) FROM RegistrationFeePayment r WHERE r.tenant.id = :tenantId AND r.status = 'AWAITING_VERIFICATION'")
    long countAwaitingVerification(Long tenantId);

    void deleteByMemberId(Long memberId);
}
