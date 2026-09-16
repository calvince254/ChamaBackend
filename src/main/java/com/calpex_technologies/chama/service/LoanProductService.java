package com.calpex_technologies.chama.service;

import com.calpex_technologies.chama.dto.LoanProductDto;
import com.calpex_technologies.chama.entity.*;
import com.calpex_technologies.chama.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class LoanProductService {

    private final LoanProductRepository loanProductRepository;
    private final LoanApprovalLevelRepository approvalLevelRepository;
    private final TenantRepository tenantRepository;
    private final RoleRepository roleRepository;
    private final MemberRepository memberRepository;

    public List<LoanProductDto> getAllLoanProducts(Long tenantId) {
        return loanProductRepository.findByTenantIdOrderByNameAsc(tenantId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<LoanProductDto> getActiveLoanProducts(Long tenantId) {
        return loanProductRepository.findByTenantIdAndActiveTrue(tenantId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public LoanProductDto getLoanProduct(Long tenantId, Long id) {
        LoanProduct product = loanProductRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Loan product not found"));
        return toDto(product);
    }

    @Transactional
    public LoanProductDto createLoanProduct(Long tenantId, LoanProductDto dto) {
        // Check for duplicate name
        if (loanProductRepository.existsByTenantIdAndNameIgnoreCase(tenantId, dto.getName())) {
            throw new RuntimeException("A loan product with this name already exists");
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        LoanProduct product = LoanProduct.builder()
                .tenant(tenant)
                .name(dto.getName())
                .description(dto.getDescription())
                .interestRate(dto.getInterestRate())
                .interestType(parseInterestType(dto.getInterestType()))
                .maxAmount(dto.getMaxAmount())
                .minAmount(dto.getMinAmount())
                .maxMultiplier(dto.getMaxMultiplier())
                .repaymentPeriod(dto.getRepaymentPeriod())
                .repaymentPeriodType(parsePeriodType(dto.getRepaymentPeriodType()))
                .requiredGuarantors(dto.getRequiredGuarantors() != null ? dto.getRequiredGuarantors() : 0)
                .penaltyEnabled(dto.isPenaltyEnabled())
                .penaltyRate(dto.getPenaltyRate())
                .penaltyType(parsePenaltyType(dto.getPenaltyType()))
                .penaltyFrequency(parsePenaltyFrequency(dto.getPenaltyFrequency()))
                .gracePeriodDays(dto.getGracePeriodDays() != null ? dto.getGracePeriodDays() : 0)
                .requiresApproval(dto.isRequiresApproval())
                .approvalLevels(dto.getApprovalLevels() != null ? dto.getApprovalLevels() : 1)
                .active(dto.isActive())
                .processingFeeEnabled(dto.isProcessingFeeEnabled())
                .processingFeeType(parseProcessingFeeType(dto.getProcessingFeeType()))
                .processingFeeValue(dto.getProcessingFeeValue())
                .processingFeeDeduction(parseProcessingFeeDeduction(dto.getProcessingFeeDeduction()))
                .build();

        product = loanProductRepository.save(product);

        // Save approval levels if configured
        if (dto.isRequiresApproval() && dto.getApprovalLevelConfig() != null) {
            saveApprovalLevels(product, dto.getApprovalLevelConfig());
        }

        return toDto(product);
    }

    @Transactional
    public LoanProductDto updateLoanProduct(Long tenantId, Long id, LoanProductDto dto) {
        LoanProduct product = loanProductRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Loan product not found"));

        // Check for duplicate name (excluding self)
        if (loanProductRepository.existsByTenantIdAndNameIgnoreCaseAndIdNot(tenantId, dto.getName(), id)) {
            throw new RuntimeException("A loan product with this name already exists");
        }

        product.setName(dto.getName());
        product.setDescription(dto.getDescription());
        product.setInterestRate(dto.getInterestRate());
        product.setInterestType(parseInterestType(dto.getInterestType()));
        product.setMaxAmount(dto.getMaxAmount());
        product.setMinAmount(dto.getMinAmount());
        product.setMaxMultiplier(dto.getMaxMultiplier());
        product.setRepaymentPeriod(dto.getRepaymentPeriod());
        product.setRepaymentPeriodType(parsePeriodType(dto.getRepaymentPeriodType()));
        product.setRequiredGuarantors(dto.getRequiredGuarantors() != null ? dto.getRequiredGuarantors() : 0);
        product.setPenaltyEnabled(dto.isPenaltyEnabled());
        product.setPenaltyRate(dto.getPenaltyRate());
        product.setPenaltyType(parsePenaltyType(dto.getPenaltyType()));
        product.setPenaltyFrequency(parsePenaltyFrequency(dto.getPenaltyFrequency()));
        product.setGracePeriodDays(dto.getGracePeriodDays() != null ? dto.getGracePeriodDays() : 0);
        product.setRequiresApproval(dto.isRequiresApproval());
        product.setApprovalLevels(dto.getApprovalLevels() != null ? dto.getApprovalLevels() : 1);
        product.setActive(dto.isActive());
        product.setProcessingFeeEnabled(dto.isProcessingFeeEnabled());
        product.setProcessingFeeType(parseProcessingFeeType(dto.getProcessingFeeType()));
        product.setProcessingFeeValue(dto.getProcessingFeeValue());
        product.setProcessingFeeDeduction(parseProcessingFeeDeduction(dto.getProcessingFeeDeduction()));

        product = loanProductRepository.save(product);

        // Update approval levels - delete existing first, then flush to ensure they're removed
        approvalLevelRepository.deleteByLoanProductId(id);
        approvalLevelRepository.flush();
        
        if (dto.isRequiresApproval() && dto.getApprovalLevelConfig() != null) {
            saveApprovalLevels(product, dto.getApprovalLevelConfig());
        }

        return toDto(product);
    }

    @Transactional
    public void deleteLoanProduct(Long tenantId, Long id) {
        LoanProduct product = loanProductRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Loan product not found"));
        
        // Delete approval levels first
        approvalLevelRepository.deleteByLoanProductId(id);
        
        loanProductRepository.delete(product);
    }

    private void saveApprovalLevels(LoanProduct product, List<LoanProductDto.ApprovalLevelDto> levels) {
        for (LoanProductDto.ApprovalLevelDto levelDto : levels) {
            LoanApprovalLevel.LoanApprovalLevelBuilder builder = LoanApprovalLevel.builder()
                    .loanProduct(product)
                    .level(levelDto.getLevel())
                    .levelName(levelDto.getLevelName())
                    .canReject(levelDto.isCanReject());

            // Use member if provided, otherwise fall back to role
            if (levelDto.getMemberId() != null && levelDto.getMemberId() > 0) {
                Member member = memberRepository.findById(levelDto.getMemberId())
                        .orElseThrow(() -> new RuntimeException("Member not found: " + levelDto.getMemberId()));
                builder.member(member);
                // Also set role from member's role for reference
                if (member.getRole() != null) {
                    builder.role(member.getRole());
                }
            } else if (levelDto.getRoleId() != null && levelDto.getRoleId() > 0) {
                Role role = roleRepository.findById(levelDto.getRoleId())
                        .orElseThrow(() -> new RuntimeException("Role not found: " + levelDto.getRoleId()));
                builder.role(role);
            } else {
                continue; // Skip if no member or role specified
            }

            approvalLevelRepository.save(builder.build());
        }
    }

    private LoanProductDto toDto(LoanProduct product) {
        List<LoanApprovalLevel> approvalLevels = approvalLevelRepository.findByLoanProductIdOrderByLevelAsc(product.getId());
        
        List<LoanProductDto.ApprovalLevelDto> levelDtos = approvalLevels.stream()
                .map(level -> {
                    LoanProductDto.ApprovalLevelDto.ApprovalLevelDtoBuilder builder = LoanProductDto.ApprovalLevelDto.builder()
                            .level(level.getLevel())
                            .levelName(level.getLevelName())
                            .canReject(level.isCanReject());
                    
                    // Include member info if available
                    if (level.getMember() != null) {
                        builder.memberId(level.getMember().getId());
                        builder.memberName(level.getMember().getName());
                    }
                    
                    // Include role info if available
                    if (level.getRole() != null) {
                        builder.roleId(level.getRole().getId());
                        builder.roleName(level.getRole().getName());
                    }
                    
                    return builder.build();
                })
                .collect(Collectors.toList());

        return LoanProductDto.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .interestRate(product.getInterestRate())
                .interestType(product.getInterestType().name())
                .maxAmount(product.getMaxAmount())
                .minAmount(product.getMinAmount())
                .maxMultiplier(product.getMaxMultiplier())
                .repaymentPeriod(product.getRepaymentPeriod())
                .repaymentPeriodType(product.getRepaymentPeriodType().name())
                .requiredGuarantors(product.getRequiredGuarantors())
                .penaltyEnabled(product.isPenaltyEnabled())
                .penaltyRate(product.getPenaltyRate())
                .penaltyType(product.getPenaltyType() != null ? product.getPenaltyType().name() : null)
                .penaltyFrequency(product.getPenaltyFrequency() != null ? product.getPenaltyFrequency().name() : null)
                .gracePeriodDays(product.getGracePeriodDays())
                .requiresApproval(product.isRequiresApproval())
                .approvalLevels(product.getApprovalLevels())
                .approvalLevelConfig(levelDtos)
                .active(product.isActive())
                .processingFeeEnabled(product.isProcessingFeeEnabled())
                .processingFeeType(product.getProcessingFeeType() != null ? product.getProcessingFeeType().name() : null)
                .processingFeeValue(product.getProcessingFeeValue())
                .processingFeeDeduction(product.getProcessingFeeDeduction() != null ? product.getProcessingFeeDeduction().name() : null)
                .build();
    }

    private LoanProduct.InterestType parseInterestType(String type) {
        if (type == null) return LoanProduct.InterestType.REDUCING_BALANCE;
        try {
            return LoanProduct.InterestType.valueOf(type);
        } catch (IllegalArgumentException e) {
            return LoanProduct.InterestType.REDUCING_BALANCE;
        }
    }

    private LoanProduct.PeriodType parsePeriodType(String type) {
        if (type == null) return LoanProduct.PeriodType.MONTHS;
        try {
            return LoanProduct.PeriodType.valueOf(type);
        } catch (IllegalArgumentException e) {
            return LoanProduct.PeriodType.MONTHS;
        }
    }

    private LoanProduct.PenaltyType parsePenaltyType(String type) {
        if (type == null) return LoanProduct.PenaltyType.PERCENTAGE_OF_OUTSTANDING;
        try {
            return LoanProduct.PenaltyType.valueOf(type);
        } catch (IllegalArgumentException e) {
            return LoanProduct.PenaltyType.PERCENTAGE_OF_OUTSTANDING;
        }
    }

    private LoanProduct.PenaltyFrequency parsePenaltyFrequency(String freq) {
        if (freq == null) return LoanProduct.PenaltyFrequency.FLAT;
        try {
            return LoanProduct.PenaltyFrequency.valueOf(freq);
        } catch (IllegalArgumentException e) {
            return LoanProduct.PenaltyFrequency.FLAT;
        }
    }

    private LoanProduct.ProcessingFeeType parseProcessingFeeType(String type) {
        if (type == null) return LoanProduct.ProcessingFeeType.FIXED;
        try {
            return LoanProduct.ProcessingFeeType.valueOf(type);
        } catch (IllegalArgumentException e) {
            return LoanProduct.ProcessingFeeType.FIXED;
        }
    }

    private LoanProduct.ProcessingFeeDeduction parseProcessingFeeDeduction(String deduction) {
        if (deduction == null) return LoanProduct.ProcessingFeeDeduction.UPFRONT;
        try {
            return LoanProduct.ProcessingFeeDeduction.valueOf(deduction);
        } catch (IllegalArgumentException e) {
            return LoanProduct.ProcessingFeeDeduction.UPFRONT;
        }
    }
}
