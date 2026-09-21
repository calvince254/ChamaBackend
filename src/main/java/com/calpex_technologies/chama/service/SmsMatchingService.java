package com.calpex_technologies.chama.service;

import com.calpex_technologies.chama.dto.CreatePaymentEventRequest;
import com.calpex_technologies.chama.dto.PaymentEventDto;
import com.calpex_technologies.chama.entity.*;
import com.calpex_technologies.chama.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for SMS-based payment matching.
 * Handles the dual-source SMS verification flow:
 * 1. Payer submits claim with reference code from their "sent to" SMS
 * 2. Treasurer device uploads "received from" SMS events
 * 3. Matcher attempts to match claims with events based on reference code
 * 4. Fallback matching uses amount + phone number within time window
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SmsMatchingService {

    private final PaymentClaimRepository paymentClaimRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final TreasurerDeviceRepository treasurerDeviceRepository;
    private final PayableService payableService;
    private final PaymentSettingsService paymentSettingsService;

    /**
     * Record a payment event from treasurer's device (incoming M-PESA SMS).
     * This is called when the treasurer's device receives a payment confirmation SMS.
     */
    @Transactional
    public PaymentEvent recordPaymentEvent(String deviceApiKey, CreatePaymentEventRequest request) {
        // Validate device
        TreasurerDevice device = treasurerDeviceRepository.findActiveByDeviceApiKey(deviceApiKey)
                .orElseThrow(() -> new RuntimeException("Invalid or inactive device API key"));

        // Update device last seen
        device.setLastSeenAt(LocalDateTime.now());
        treasurerDeviceRepository.save(device);

        // Check if this event already exists (duplicate SMS)
        Optional<PaymentEvent> existing = paymentEventRepository.findByReferenceCode(request.getReferenceCode());
        if (existing.isPresent()) {
            log.debug("Payment event with reference {} already exists", request.getReferenceCode());
            return existing.get();
        }

        // Create payment event
        PaymentEvent event = PaymentEvent.builder()
                .treasurerDevice(device)
                .tenant(device.getTenant())
                .payerNumber(normalizePhoneNumber(request.getPayerNumber()))
                .payerName(request.getPayerName())
                .amount(request.getAmount())
                .referenceCode(request.getReferenceCode().toUpperCase())
                .rawSmsText(request.getRawSmsText())
                .capturedAt(request.getCapturedAt())
                .matched(false)
                .build();

        PaymentEvent saved = paymentEventRepository.save(event);
        log.info("Recorded payment event: {} from {} amount {}", 
                saved.getReferenceCode(), saved.getPayerNumber(), saved.getAmount());

        // Attempt to match with pending claims
        attemptMatchByReferenceCode(saved);

        return saved;
    }

    /**
     * Update a payment claim with the reference code from payer's SMS.
     * This moves the claim from PENDING_SMS to PENDING_MATCH and triggers matching.
     */
    @Transactional
    public PaymentClaim updateClaimWithReference(Long claimId, String referenceCode, 
                                                   LocalDateTime capturedAt, String payerPhone) {
        PaymentClaim claim = paymentClaimRepository.findByIdWithPayable(claimId)
                .orElseThrow(() -> new RuntimeException("Payment claim not found: " + claimId));

        if (claim.getStatus() != PaymentClaimStatus.PENDING_SMS) {
            throw new RuntimeException("Claim is not in PENDING_SMS status");
        }

        claim.setReferenceCode(referenceCode.toUpperCase());
        claim.setCapturedAt(capturedAt);
        claim.setPayerPhone(normalizePhoneNumber(payerPhone));
        claim.setStatus(PaymentClaimStatus.PENDING_MATCH);

        PaymentClaim saved = paymentClaimRepository.save(claim);
        log.info("Updated claim {} with reference code {}", claimId, referenceCode);

        // Attempt to match with existing events
        attemptMatchClaimWithEvents(saved);

        return saved;
    }

    /**
     * Attempt to match a payment event with pending claims by reference code.
     */
    @Transactional
    public boolean attemptMatchByReferenceCode(PaymentEvent event) {
        if (event.isMatched()) {
            return false;
        }

        List<PaymentClaim> matchingClaims = paymentClaimRepository
                .findByReferenceCodeAndStatus(event.getReferenceCode(), PaymentClaimStatus.PENDING_MATCH);

        if (matchingClaims.isEmpty()) {
            log.debug("No matching claims found for reference {}", event.getReferenceCode());
            return false;
        }

        // If multiple claims have the same reference (unlikely), match the first one
        // In practice, MPESA reference codes are unique
        PaymentClaim claim = matchingClaims.get(0);

        // Verify amounts match (or are within tolerance)
        if (!amountsMatch(claim.getAmount(), event.getAmount())) {
            log.warn("Amount mismatch for reference {}: claim={}, event={}", 
                    event.getReferenceCode(), claim.getAmount(), event.getAmount());
            // Still match but log the warning - amounts should match exactly for MPESA
        }

        return performMatch(claim, event, null);
    }

    /**
     * Attempt to match a claim with existing payment events.
     */
    @Transactional
    public boolean attemptMatchClaimWithEvents(PaymentClaim claim) {
        if (claim.getStatus() != PaymentClaimStatus.PENDING_MATCH) {
            return false;
        }

        List<PaymentEvent> matchingEvents = paymentEventRepository
                .findByReferenceCodeAndMatchedFalse(claim.getReferenceCode());

        if (matchingEvents.isEmpty()) {
            log.debug("No matching events found for claim {} with reference {}", 
                    claim.getId(), claim.getReferenceCode());
            return false;
        }

        PaymentEvent event = matchingEvents.get(0);
        return performMatch(claim, event, null);
    }

    /**
     * Perform the actual match between a claim and event.
     */
    @Transactional
    public boolean performMatch(PaymentClaim claim, PaymentEvent event, Member matchedBy) {
        LocalDateTime now = LocalDateTime.now();

        // Update claim
        claim.setStatus(PaymentClaimStatus.MATCHED);
        claim.setMatchedAt(now);
        claim.setMatchedEvent(event);
        claim.setMpesaReceipt(event.getReferenceCode());
        paymentClaimRepository.save(claim);

        // Update event
        event.setMatched(true);
        event.setMatchedAt(now);
        event.setMatchedBy(matchedBy);
        paymentEventRepository.save(event);

        // Mark payable as paid
        payableService.markAsPaid(claim.getPayable().getId());

        log.info("MATCHED: Claim {} with Event {} (ref: {})", 
                claim.getId(), event.getId(), event.getReferenceCode());

        return true;
    }

    /**
     * Manual match - admin matches an unmatched event to a claim.
     */
    @Transactional
    public void manualMatch(Long eventId, Long claimId, Member matchedBy) {
        PaymentEvent event = paymentEventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Payment event not found: " + eventId));

        PaymentClaim claim = paymentClaimRepository.findByIdWithPayable(claimId)
                .orElseThrow(() -> new RuntimeException("Payment claim not found: " + claimId));

        if (event.isMatched()) {
            throw new RuntimeException("Event is already matched");
        }

        if (claim.getStatus() == PaymentClaimStatus.MATCHED) {
            throw new RuntimeException("Claim is already matched");
        }

        // Update claim reference code to match event
        claim.setReferenceCode(event.getReferenceCode());
        
        performMatch(claim, event, matchedBy);
        
        log.info("Manual match performed by {} for event {} and claim {}", 
                matchedBy.getName(), eventId, claimId);
    }

    /**
     * Fallback matching for claims that haven't been matched within time window.
     * Uses amount + phone number instead of reference code.
     */
    @Transactional
    public void attemptFallbackMatching(PaymentClaim claim) {
        if (claim.getStatus() != PaymentClaimStatus.PENDING_MATCH) {
            return;
        }

        Long tenantId = claim.getPayable().getTenant().getId();
        PaymentSettings settings = paymentSettingsService.getSettingsEntity(tenantId);
        int windowMinutes = settings.getFallbackMatchWindowMinutes();

        LocalDateTime startTime = claim.getCapturedAt().minusMinutes(windowMinutes);
        LocalDateTime endTime = claim.getCapturedAt().plusMinutes(windowMinutes);

        // Try to find matching event by amount and phone number
        if (claim.getPayerPhone() != null) {
            List<PaymentEvent> potentialMatches = paymentEventRepository
                    .findByPayerNumberAmountAndTimeWindow(
                            tenantId,
                            normalizePhoneNumber(claim.getPayerPhone()),
                            claim.getAmount(),
                            startTime,
                            endTime);

            if (potentialMatches.size() == 1) {
                // Unambiguous match
                PaymentEvent event = potentialMatches.get(0);
                log.info("Fallback match found for claim {} with event {}", 
                        claim.getId(), event.getId());
                performMatch(claim, event, null);
            } else if (potentialMatches.size() > 1) {
                // Ambiguous - move to manual review
                claim.setStatus(PaymentClaimStatus.MANUAL_REVIEW);
                paymentClaimRepository.save(claim);
                log.info("Claim {} moved to MANUAL_REVIEW due to ambiguous fallback matches", 
                        claim.getId());
            }
        }

        // If still not matched, try by amount only
        if (claim.getStatus() == PaymentClaimStatus.PENDING_MATCH) {
            List<PaymentEvent> amountMatches = paymentEventRepository
                    .findByAmountAndTimeWindow(tenantId, claim.getAmount(), startTime, endTime);

            if (amountMatches.size() == 1) {
                PaymentEvent event = amountMatches.get(0);
                log.info("Fallback match (amount only) found for claim {} with event {}", 
                        claim.getId(), event.getId());
                performMatch(claim, event, null);
            } else if (amountMatches.isEmpty()) {
                // No matches found - move to manual review
                claim.setStatus(PaymentClaimStatus.MANUAL_REVIEW);
                paymentClaimRepository.save(claim);
                log.info("Claim {} moved to MANUAL_REVIEW - no matching events found", 
                        claim.getId());
            }
        }
    }

    /**
     * Get unmatched events for a tenant (for treasurer's dashboard).
     */
    @Transactional(readOnly = true)
    public Page<PaymentEventDto> getUnmatchedEvents(Long tenantId, Pageable pageable) {
        return paymentEventRepository.findUnmatchedByTenantId(tenantId, pageable)
                .map(this::toEventDto);
    }

    /**
     * Get events needing manual review (older than match window).
     */
    @Transactional(readOnly = true)
    public List<PaymentEventDto> getEventsNeedingReview(Long tenantId) {
        PaymentSettings settings = paymentSettingsService.getSettingsEntity(tenantId);
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(settings.getFallbackMatchWindowMinutes());
        
        return paymentEventRepository.findUnmatchedOlderThan(tenantId, threshold)
                .stream()
                .map(this::toEventDto)
                .collect(Collectors.toList());
    }

    /**
     * Scheduled job to expire old claims and attempt fallback matching.
     * Runs every 2 minutes.
     */
    @Scheduled(fixedRate = 120000) // 2 minutes
    @Transactional
    public void processClaimsJob() {
        LocalDateTime now = LocalDateTime.now();

        // Expire old claims
        int expired = paymentClaimRepository.expireClaims(now);
        if (expired > 0) {
            log.info("Expired {} payment claims", expired);
        }

        // Attempt fallback matching for claims older than 15 minutes
        LocalDateTime threshold = now.minusMinutes(15);
        List<PaymentClaim> claimsForFallback = paymentClaimRepository
                .findClaimsForFallbackMatching(threshold, now);

        for (PaymentClaim claim : claimsForFallback) {
            try {
                attemptFallbackMatching(claim);
            } catch (Exception e) {
                log.error("Error processing fallback matching for claim {}: {}", 
                        claim.getId(), e.getMessage());
            }
        }
    }

    /**
     * Normalize phone number to consistent format (254XXXXXXXXX).
     */
    private String normalizePhoneNumber(String phone) {
        if (phone == null) return null;
        
        // Remove spaces and special characters
        phone = phone.replaceAll("[\\s\\-()]+", "");
        
        // Handle different formats
        if (phone.startsWith("+")) {
            phone = phone.substring(1);
        }
        if (phone.startsWith("0")) {
            phone = "254" + phone.substring(1);
        }
        if (phone.length() == 9) {
            phone = "254" + phone;
        }
        
        return phone;
    }

    /**
     * Check if two amounts match (exact match for MPESA).
     */
    private boolean amountsMatch(BigDecimal amount1, BigDecimal amount2) {
        if (amount1 == null || amount2 == null) return false;
        return amount1.compareTo(amount2) == 0;
    }

    private PaymentEventDto toEventDto(PaymentEvent event) {
        return PaymentEventDto.builder()
                .id(event.getId())
                .treasurerDeviceId(event.getTreasurerDevice().getId())
                .treasurerDeviceName(event.getTreasurerDevice().getDeviceName())
                .treasurerName(event.getTreasurerDevice().getMember().getName())
                .payerNumber(event.getPayerNumber())
                .payerName(event.getPayerName())
                .amount(event.getAmount())
                .referenceCode(event.getReferenceCode())
                .capturedAt(event.getCapturedAt())
                .matched(event.isMatched())
                .matchedClaimId(event.getMatchedClaim() != null ? event.getMatchedClaim().getId() : null)
                .matchedById(event.getMatchedBy() != null ? event.getMatchedBy().getId() : null)
                .matchedByName(event.getMatchedBy() != null ? event.getMatchedBy().getName() : null)
                .matchedAt(event.getMatchedAt())
                .createdAt(event.getCreatedAt())
                .build();
    }
}
