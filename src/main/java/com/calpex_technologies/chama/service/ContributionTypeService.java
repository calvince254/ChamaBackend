package com.calpex_technologies.chama.service;

import com.calpex_technologies.chama.dto.ContributionTypeDto;
import com.calpex_technologies.chama.entity.ContributionType;
import com.calpex_technologies.chama.entity.Tenant;
import com.calpex_technologies.chama.repository.ContributionTypeRepository;
import com.calpex_technologies.chama.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContributionTypeService {

    private final ContributionTypeRepository contributionTypeRepository;
    private final TenantRepository tenantRepository;

    public List<ContributionTypeDto> getAllContributionTypes(Long tenantId) {
        return contributionTypeRepository.findByTenantIdOrderByNameAsc(tenantId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<ContributionTypeDto> getActiveContributionTypes(Long tenantId) {
        return contributionTypeRepository.findByTenantIdAndActiveTrue(tenantId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public ContributionTypeDto getContributionType(Long tenantId, Long id) {
        ContributionType ct = contributionTypeRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Contribution type not found"));
        return toDto(ct);
    }

    @Transactional
    public ContributionTypeDto createContributionType(Long tenantId, ContributionTypeDto dto) {
        if (contributionTypeRepository.existsByTenantIdAndNameIgnoreCase(tenantId, dto.getName())) {
            throw new RuntimeException("A contribution type with this name already exists");
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        ContributionType ct = ContributionType.builder()
                .tenant(tenant)
                .name(dto.getName())
                .description(dto.getDescription())
                .amount(dto.getAmount())
                .frequency(ContributionType.Frequency.valueOf(dto.getFrequency()))
                .dueDay(dto.getDueDay())
                .active(true)
                .lateFineEnabled(dto.isLateFineEnabled())
                .lateFineAmount(dto.getLateFineAmount())
                .gracePeriodDays(dto.getGracePeriodDays() != null ? dto.getGracePeriodDays() : 0)
                .build();

        ct = contributionTypeRepository.save(ct);
        return toDto(ct);
    }

    @Transactional
    public ContributionTypeDto updateContributionType(Long tenantId, Long id, ContributionTypeDto dto) {
        ContributionType ct = contributionTypeRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Contribution type not found"));

        // Check name uniqueness if changed
        if (!ct.getName().equalsIgnoreCase(dto.getName()) &&
            contributionTypeRepository.existsByTenantIdAndNameIgnoreCaseAndIdNot(tenantId, dto.getName(), id)) {
            throw new RuntimeException("A contribution type with this name already exists");
        }

        ct.setName(dto.getName());
        ct.setDescription(dto.getDescription());
        ct.setAmount(dto.getAmount());
        ct.setFrequency(ContributionType.Frequency.valueOf(dto.getFrequency()));
        ct.setDueDay(dto.getDueDay());
        ct.setActive(dto.isActive());
        ct.setLateFineEnabled(dto.isLateFineEnabled());
        ct.setLateFineAmount(dto.getLateFineAmount());
        ct.setGracePeriodDays(dto.getGracePeriodDays() != null ? dto.getGracePeriodDays() : 0);

        ct = contributionTypeRepository.save(ct);
        return toDto(ct);
    }

    @Transactional
    public void deleteContributionType(Long tenantId, Long id) {
        ContributionType ct = contributionTypeRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Contribution type not found"));
        // Soft delete
        ct.setActive(false);
        contributionTypeRepository.save(ct);
    }

    private ContributionTypeDto toDto(ContributionType ct) {
        return ContributionTypeDto.builder()
                .id(ct.getId())
                .name(ct.getName())
                .description(ct.getDescription())
                .amount(ct.getAmount())
                .frequency(ct.getFrequency().name())
                .dueDay(ct.getDueDay())
                .active(ct.isActive())
                .lateFineEnabled(ct.isLateFineEnabled())
                .lateFineAmount(ct.getLateFineAmount())
                .gracePeriodDays(ct.getGracePeriodDays())
                .build();
    }
}
