package com.calpex_technologies.chama.service;

import com.calpex_technologies.chama.dto.FineTypeDto;
import com.calpex_technologies.chama.entity.FineType;
import com.calpex_technologies.chama.entity.Tenant;
import com.calpex_technologies.chama.repository.FineTypeRepository;
import com.calpex_technologies.chama.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FineTypeService {

    private final FineTypeRepository fineTypeRepository;
    private final TenantRepository tenantRepository;

    public List<FineTypeDto> getAllFineTypes(Long tenantId) {
        return fineTypeRepository.findByTenantIdOrderByNameAsc(tenantId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<FineTypeDto> getActiveFineTypes(Long tenantId) {
        return fineTypeRepository.findByTenantIdAndActiveTrue(tenantId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public FineTypeDto getFineType(Long tenantId, Long id) {
        FineType fineType = fineTypeRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Fine type not found"));
        return toDto(fineType);
    }

    @Transactional
    public FineTypeDto createFineType(Long tenantId, FineTypeDto dto) {
        if (fineTypeRepository.existsByTenantIdAndNameIgnoreCase(tenantId, dto.getName())) {
            throw new RuntimeException("A fine type with this name already exists");
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        FineType.TriggerType triggerType = null;
        if (dto.isAutomatic() && dto.getTriggerType() != null) {
            triggerType = FineType.TriggerType.valueOf(dto.getTriggerType());
        }

        FineType fineType = FineType.builder()
                .tenant(tenant)
                .name(dto.getName())
                .description(dto.getDescription())
                .amount(dto.getAmount())
                .active(true)
                .automatic(dto.isAutomatic())
                .triggerType(triggerType)
                .build();

        fineType = fineTypeRepository.save(fineType);
        return toDto(fineType);
    }

    @Transactional
    public FineTypeDto updateFineType(Long tenantId, Long id, FineTypeDto dto) {
        FineType fineType = fineTypeRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Fine type not found"));

        FineType.TriggerType triggerType = null;
        if (dto.isAutomatic() && dto.getTriggerType() != null) {
            triggerType = FineType.TriggerType.valueOf(dto.getTriggerType());
        }

        fineType.setName(dto.getName());
        fineType.setDescription(dto.getDescription());
        fineType.setAmount(dto.getAmount());
        fineType.setActive(dto.isActive());
        fineType.setAutomatic(dto.isAutomatic());
        fineType.setTriggerType(triggerType);

        fineType = fineTypeRepository.save(fineType);
        return toDto(fineType);
    }

    @Transactional
    public void deleteFineType(Long tenantId, Long id) {
        FineType fineType = fineTypeRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Fine type not found"));
        // Soft delete - just deactivate
        fineType.setActive(false);
        fineTypeRepository.save(fineType);
    }

    private FineTypeDto toDto(FineType fineType) {
        return FineTypeDto.builder()
                .id(fineType.getId())
                .name(fineType.getName())
                .description(fineType.getDescription())
                .amount(fineType.getAmount())
                .active(fineType.isActive())
                .automatic(fineType.isAutomatic())
                .triggerType(fineType.getTriggerType() != null ? fineType.getTriggerType().name() : null)
                .build();
    }
}
