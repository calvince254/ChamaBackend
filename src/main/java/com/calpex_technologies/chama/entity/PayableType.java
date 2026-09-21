package com.calpex_technologies.chama.entity;

/**
 * Types of payable items in the system.
 * Each type corresponds to a specific module's due/payment.
 */
public enum PayableType {
    CONTRIBUTION,           // Monthly/periodic contribution
    LOAN_REPAYMENT,         // Loan repayment installment
    WELFARE_DUE,            // Welfare event contribution
    MGR_CONTRIBUTION,       // Merry-go-round contribution
    MGR_FINE,               // Merry-go-round fine
    SHARE_PURCHASE,         // Share purchase
    INVESTMENT_BUYIN,       // Investment buy-in
    LICENSE_INVOICE,        // Platform license invoice
    REGISTRATION_FEE,       // Member registration fee
    PENALTY,                // Contribution penalty
    LOAN_PENALTY,           // Loan penalty
    MEETING_FINE            // Meeting attendance fine
}
