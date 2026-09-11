package com.calpex_technologies.chama.service;

import com.calpex_technologies.chama.dto.WelfareContributionDto;
import com.calpex_technologies.chama.dto.WelfareEventDto;
import com.calpex_technologies.chama.entity.Member;
import com.calpex_technologies.chama.entity.Tenant;
import com.calpex_technologies.chama.entity.WelfareContribution;
import com.calpex_technologies.chama.entity.WelfareEvent;
import com.calpex_technologies.chama.repository.MemberRepository;
import com.calpex_technologies.chama.repository.TenantRepository;
import com.calpex_technologies.chama.repository.WelfareContributionRepository;
import com.calpex_technologies.chama.repository.WelfareEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WelfareService {

    private final WelfareEventRepository welfareEventRepository;
    private final WelfareContributionRepository welfareContributionRepository;
    private final MemberRepository memberRepository;
    private final TenantRepository tenantRepository;

    public List<WelfareEventDto> getAllWelfareEvents(Long tenantId) {
        return welfareEventRepository.findByTenantIdOrderByDateReportedDesc(tenantId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public WelfareEventDto getWelfareEvent(Long id, Long tenantId) {
        WelfareEvent event = welfareEventRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Welfare event not found"));
        return toDto(event);
    }

    @Transactional
    public WelfareEventDto createWelfareEvent(WelfareEventDto dto, Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        Member member = memberRepository.findByIdAndTenantId(dto.getMemberId(), tenantId)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        // Determine which members are involved
        List<Member> involvedMembers;
        boolean allMembersInvolved = dto.getAllMembersInvolved() == null || dto.getAllMembersInvolved();
        
        if (allMembersInvolved) {
            involvedMembers = memberRepository.findActiveByTenantId(tenantId);
        } else if (dto.getInvolvedMemberIds() != null && !dto.getInvolvedMemberIds().isEmpty()) {
            involvedMembers = memberRepository.findAllById(dto.getInvolvedMemberIds()).stream()
                    .filter(m -> m.getTenant().getId().equals(tenantId))
                    .collect(Collectors.toList());
        } else {
            // Default to all active members if no specific selection
            involvedMembers = memberRepository.findActiveByTenantId(tenantId);
            allMembersInvolved = true;
        }

        BigDecimal totalExpected = dto.getAmountPerMember().multiply(BigDecimal.valueOf(involvedMembers.size()));

        WelfareEvent event = WelfareEvent.builder()
                .member(member)
                .eventType(WelfareEvent.EventType.valueOf(dto.getEventType()))
                .relation(dto.getRelation())
                .dateReported(LocalDate.now())
                .amountPerMember(dto.getAmountPerMember())
                .totalExpected(totalExpected)
                .totalCollected(BigDecimal.ZERO)
                .status(WelfareEvent.WelfareStatus.OPEN)
                .description(dto.getDescription())
                .allMembersInvolved(allMembersInvolved)
                .tenant(tenant)
                .build();

        event = welfareEventRepository.save(event);

        // Create contribution records for all involved members
        WelfareEvent savedEvent = event;
        for (Member involvedMember : involvedMembers) {
            WelfareContribution contribution = WelfareContribution.builder()
                    .welfareEvent(savedEvent)
                    .member(involvedMember)
                    .amountExpected(dto.getAmountPerMember())
                    .amountPaid(BigDecimal.ZERO)
                    .paid(false)
                    .tenant(tenant)
                    .build();
            welfareContributionRepository.save(contribution);
        }

        return toDto(event);
    }

    @Transactional
    public WelfareEventDto updateWelfareEvent(Long id, WelfareEventDto dto, Long tenantId) {
        WelfareEvent event = welfareEventRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Welfare event not found"));

        event.setEventType(WelfareEvent.EventType.valueOf(dto.getEventType()));
        event.setRelation(dto.getRelation());
        event.setAmountPerMember(dto.getAmountPerMember());
        event.setTotalCollected(dto.getTotalCollected());
        event.setStatus(WelfareEvent.WelfareStatus.valueOf(dto.getStatus()));
        event.setDescription(dto.getDescription());

        event = welfareEventRepository.save(event);
        return toDto(event);
    }

    @Transactional
    public WelfareEventDto closeEvent(Long id, Long tenantId) {
        WelfareEvent event = welfareEventRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Welfare event not found"));
        event.setStatus(WelfareEvent.WelfareStatus.CLOSED);
        event = welfareEventRepository.save(event);
        return toDto(event);
    }

    // ==================== CONTRIBUTION MANAGEMENT ====================

    public List<WelfareContributionDto> getEventContributions(Long eventId, Long tenantId) {
        // Verify event exists and belongs to tenant
        welfareEventRepository.findByIdAndTenantId(eventId, tenantId)
                .orElseThrow(() -> new RuntimeException("Welfare event not found"));

        return welfareContributionRepository.findByWelfareEventIdAndTenantId(eventId, tenantId).stream()
                .map(this::toContributionDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public WelfareContributionDto updateContributionPayment(Long eventId, Long memberId, Boolean paid, Long tenantId) {
        WelfareEvent event = welfareEventRepository.findByIdAndTenantId(eventId, tenantId)
                .orElseThrow(() -> new RuntimeException("Welfare event not found"));

        WelfareContribution contribution = welfareContributionRepository
                .findByWelfareEventIdAndMemberIdAndTenantId(eventId, memberId, tenantId)
                .orElseThrow(() -> new RuntimeException("Contribution not found"));

        // Update paid status
        contribution.setPaid(paid);
        if (paid) {
            contribution.setAmountPaid(contribution.getAmountExpected());
            contribution.setPaidDate(LocalDateTime.now());
        } else {
            contribution.setAmountPaid(BigDecimal.ZERO);
            contribution.setPaidDate(null);
        }

        contribution = welfareContributionRepository.save(contribution);

        // Recalculate total collected
        recalculateTotalCollected(event, tenantId);

        return toContributionDto(contribution);
    }

    @Transactional
    public void markAllAsPaid(Long eventId, Long tenantId) {
        WelfareEvent event = welfareEventRepository.findByIdAndTenantId(eventId, tenantId)
                .orElseThrow(() -> new RuntimeException("Welfare event not found"));

        List<WelfareContribution> contributions = welfareContributionRepository
                .findByWelfareEventIdAndTenantId(eventId, tenantId);

        LocalDateTime now = LocalDateTime.now();
        for (WelfareContribution c : contributions) {
            if (!c.getPaid()) {
                c.setPaid(true);
                c.setAmountPaid(c.getAmountExpected());
                c.setPaidDate(now);
                welfareContributionRepository.save(c);
            }
        }

        recalculateTotalCollected(event, tenantId);
    }

    private void recalculateTotalCollected(WelfareEvent event, Long tenantId) {
        List<WelfareContribution> contributions = welfareContributionRepository
                .findByWelfareEventIdAndTenantId(event.getId(), tenantId);

        BigDecimal totalCollected = contributions.stream()
                .map(WelfareContribution::getAmountPaid)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        event.setTotalCollected(totalCollected);
        welfareEventRepository.save(event);
    }

    private WelfareEventDto toDto(WelfareEvent event) {
        // Get contribution stats
        List<WelfareContribution> contributions = welfareContributionRepository
                .findByWelfareEventIdAndTenantId(event.getId(), event.getTenant().getId());
        
        int totalInvolved = contributions.size();
        int paidCount = (int) contributions.stream().filter(WelfareContribution::getPaid).count();

        return WelfareEventDto.builder()
                .id(event.getId())
                .memberId(event.getMember().getId())
                .memberName(event.getMember().getName())
                .eventType(event.getEventType().name())
                .relation(event.getRelation())
                .dateReported(event.getDateReported().toString())
                .amountPerMember(event.getAmountPerMember())
                .totalExpected(event.getTotalExpected())
                .totalCollected(event.getTotalCollected())
                .status(event.getStatus().name())
                .description(event.getDescription())
                .allMembersInvolved(event.getAllMembersInvolved())
                .totalInvolved(totalInvolved)
                .paidCount(paidCount)
                .build();
    }

    private WelfareContributionDto toContributionDto(WelfareContribution contribution) {
        return WelfareContributionDto.builder()
                .id(contribution.getId())
                .welfareEventId(contribution.getWelfareEvent().getId())
                .memberId(contribution.getMember().getId())
                .memberName(contribution.getMember().getName())
                .memberNo(contribution.getMember().getMemberNo())
                .amountExpected(contribution.getAmountExpected())
                .amountPaid(contribution.getAmountPaid())
                .paid(contribution.getPaid())
                .paidDate(contribution.getPaidDate() != null ? contribution.getPaidDate().toString() : null)
                .paymentReference(contribution.getPaymentReference())
                .build();
    }
}
