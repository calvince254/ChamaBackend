package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.InvestmentApproval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvestmentApprovalRepository extends JpaRepository<InvestmentApproval, Long> {
    
    List<InvestmentApproval> findByInvestmentId(Long investmentId);
    
    Optional<InvestmentApproval> findByInvestmentIdAndApproverId(Long investmentId, Long approverId);
    
    // Find pending approvals for a specific member
    @Query("SELECT a FROM InvestmentApproval a WHERE a.approver.id = :memberId AND a.decision = 'PENDING' AND a.investment.approvalStatus = 'PENDING'")
    List<InvestmentApproval> findPendingApprovalsForMember(Long memberId);
    
    // Count pending approvals for a member
    @Query("SELECT COUNT(a) FROM InvestmentApproval a WHERE a.approver.id = :memberId AND a.decision = 'PENDING' AND a.investment.approvalStatus = 'PENDING'")
    long countPendingApprovalsForMember(Long memberId);
    
    // Find all approvals for investments in a tenant
    @Query("SELECT a FROM InvestmentApproval a WHERE a.investment.tenant.id = :tenantId")
    List<InvestmentApproval> findByTenantId(Long tenantId);
}
