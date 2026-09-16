package com.calpex_technologies.chama.repository;

import com.calpex_technologies.chama.entity.LoanApprovalLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoanApprovalLevelRepository extends JpaRepository<LoanApprovalLevel, Long> {
    
    List<LoanApprovalLevel> findByLoanProductIdOrderByLevelAsc(Long loanProductId);
    
    Optional<LoanApprovalLevel> findByLoanProductIdAndLevel(Long loanProductId, Integer level);
    
    @Modifying
    void deleteByLoanProductId(Long loanProductId);
    
    boolean existsByLoanProductIdAndRoleId(Long loanProductId, Long roleId);
}
