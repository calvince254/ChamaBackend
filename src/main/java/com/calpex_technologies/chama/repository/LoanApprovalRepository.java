package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.LoanApproval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoanApprovalRepository extends JpaRepository<LoanApproval, Long> {
    
    List<LoanApproval> findByLoanIdOrderByLevelAsc(Long loanId);
    
    Optional<LoanApproval> findByLoanIdAndLevel(Long loanId, Integer level);
    
    List<LoanApproval> findByApproverId(Long approverId);
    
    boolean existsByLoanIdAndLevel(Long loanId, Integer level);
}
