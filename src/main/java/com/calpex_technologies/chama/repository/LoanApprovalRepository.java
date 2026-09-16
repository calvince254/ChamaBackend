package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.LoanApproval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoanApprovalRepository extends JpaRepository<LoanApproval, Long> {
    
    // Order by approval type (guarantors first) then by level
    @Query("SELECT la FROM LoanApproval la WHERE la.loan.id = :loanId ORDER BY la.approvalType ASC, la.level ASC NULLS FIRST, la.actionDate ASC")
    List<LoanApproval> findByLoanIdOrderByTypeAndLevel(@Param("loanId") Long loanId);
    
    // Legacy method - keep for backward compatibility
    List<LoanApproval> findByLoanIdOrderByLevelAsc(Long loanId);
    
    Optional<LoanApproval> findByLoanIdAndLevel(Long loanId, Integer level);
    
    List<LoanApproval> findByApproverId(Long approverId);
    
    boolean existsByLoanIdAndLevel(Long loanId, Integer level);
    
    // Check if a guarantor has already approved this loan
    @Query("SELECT CASE WHEN COUNT(la) > 0 THEN true ELSE false END FROM LoanApproval la " +
           "WHERE la.loan.id = :loanId AND la.approver.id = :approverId AND la.approvalType = 'GUARANTOR'")
    boolean existsGuarantorApproval(@Param("loanId") Long loanId, @Param("approverId") Long approverId);
    
    // Get all guarantor approvals for a loan
    @Query("SELECT la FROM LoanApproval la WHERE la.loan.id = :loanId AND la.approvalType = 'GUARANTOR'")
    List<LoanApproval> findGuarantorApprovalsByLoanId(@Param("loanId") Long loanId);
    
    // Get all approver (level-based) approvals for a loan
    @Query("SELECT la FROM LoanApproval la WHERE la.loan.id = :loanId AND la.approvalType = 'APPROVER' ORDER BY la.level ASC")
    List<LoanApproval> findApproverApprovalsByLoanId(@Param("loanId") Long loanId);
}
