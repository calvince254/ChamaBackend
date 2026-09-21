package com.calpex_technologies.chama.entity;

/**
 * Status of a payment claim.
 */
public enum PaymentClaimStatus {
    PENDING_SMS,        // Claim created, waiting for payer's SMS
    PENDING_MATCH,      // Payer SMS received, waiting for treasurer's SMS match
    MATCHED,            // Both SMS matched, payment confirmed
    EXPIRED,            // Claim expired without match
    MANUAL_REVIEW,      // Requires admin manual review
    FAILED ,             // STK Push or other payment failed
    PENDING_CALLBACK
}
