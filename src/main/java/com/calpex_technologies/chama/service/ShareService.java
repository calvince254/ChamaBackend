package com.calpex_technologies.chama.service;

import com.calpex_technologies.chama.dto.ShareDto;
import com.calpex_technologies.chama.entity.Member;
import com.calpex_technologies.chama.entity.Share;
import com.calpex_technologies.chama.entity.Tenant;
import com.calpex_technologies.chama.entity.Transaction;
import com.calpex_technologies.chama.repository.MemberRepository;
import com.calpex_technologies.chama.repository.ShareRepository;
import com.calpex_technologies.chama.repository.TenantRepository;
import com.calpex_technologies.chama.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShareService {

    private final ShareRepository shareRepository;
    private final MemberRepository memberRepository;
    private final TenantRepository tenantRepository;
    private final TransactionRepository transactionRepository;

    public List<ShareDto> getAllShares(Long tenantId) {
        return shareRepository.findByTenantIdOrderByTotalValueDesc(tenantId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public ShareDto getShare(Long id, Long tenantId) {
        Share share = shareRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Share not found"));
        return toDto(share);
    }

    @Transactional
    public ShareDto purchaseShares(Long memberId, int quantity, BigDecimal shareValue, Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        Member member = memberRepository.findByIdAndTenantId(memberId, tenantId)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        Share share = shareRepository.findByMemberIdAndTenantId(memberId, tenantId)
                .orElseGet(() -> Share.builder()
                        .member(member)
                        .shareValue(shareValue)
                        .tenant(tenant)
                        .build());

        share.setSharesOwned(share.getSharesOwned() + quantity);
        share.setShareValue(shareValue);
        share.setTotalValue(shareValue.multiply(BigDecimal.valueOf(share.getSharesOwned())));
        share.setLastPurchaseDate(LocalDate.now());

        share = shareRepository.save(share);

        // Update member total shares
        member.setTotalShares(share.getTotalValue());
        memberRepository.save(member);

        // Create transaction
        BigDecimal purchaseAmount = shareValue.multiply(BigDecimal.valueOf(quantity));
        Transaction transaction = Transaction.builder()
                .date(LocalDate.now())
                .type(Transaction.TransactionType.SHARE_PURCHASE)
                .description("Share purchase by " + member.getName() + " (" + quantity + " shares)")
                .amount(purchaseAmount)
                .direction(Transaction.TransactionDirection.IN)
                .member(member)
                .referenceId(share.getId())
                .referenceType("SHARE")
                .tenant(tenant)
                .build();
        transactionRepository.save(transaction);

        return toDto(share);
    }

    private ShareDto toDto(Share share) {
        return ShareDto.builder()
                .id(share.getId())
                .memberId(share.getMember().getId())
                .memberName(share.getMember().getName())
                .sharesOwned(share.getSharesOwned())
                .shareValue(share.getShareValue())
                .totalValue(share.getTotalValue())
                .lastPurchaseDate(share.getLastPurchaseDate() != null ? share.getLastPurchaseDate().toString() : null)
                .build();
    }
}
