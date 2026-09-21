package com.calpex_technologies.chama.entity;

/**
 * Status of a payable item.
 */
public enum PayableStatus {
    OUTSTANDING,            // Payment is due, not yet initiated
    AWAITING_CONFIRMATION,  // Payment initiated, waiting for confirmation
    PAID,                   // Payment confirmed and applied
    CANCELLED,              // Payment was cancelled
    EXPIRED                 // Payment claim expired without confirmation
}
