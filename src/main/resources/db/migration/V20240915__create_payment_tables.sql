-- Migration: Create Payment System Tables
-- Version: 20240915
-- Description: Tables for 3-option payment system (In-App SMS, STK Push, Manual)

-- =====================================================
-- Payment Settings Table
-- Admin-configurable payment options per tenant
-- =====================================================
CREATE TABLE IF NOT EXISTS payment_settings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    
    -- Payment method toggles
    in_app_sms_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    stk_push_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    manual_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    
    -- STK Push (Daraja) Configuration
    daraja_consumer_key VARCHAR(100),
    daraja_consumer_secret VARCHAR(255),
    business_short_code VARCHAR(20),
    lipa_na_mpesa_passkey VARCHAR(255),
    account_reference VARCHAR(50),
    transaction_desc VARCHAR(100),
    callback_url VARCHAR(255),
    
    -- In-App SMS Configuration
    treasurer_phone VARCHAR(15),
    treasurer_name VARCHAR(100),
    claim_expiry_minutes INT NOT NULL DEFAULT 30,
    fallback_match_window_minutes INT NOT NULL DEFAULT 20,
    
    -- General Settings
    sms_notifications_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    push_notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    default_payment_method VARCHAR(20) DEFAULT 'MANUAL',
    
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME,
    
    CONSTRAINT fk_payment_settings_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT uk_payment_settings_tenant UNIQUE (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_payment_settings_tenant ON payment_settings(tenant_id);


-- =====================================================
-- Payable Table
-- Universal payment abstraction linking to any module's due
-- =====================================================
CREATE TABLE IF NOT EXISTS payable (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    payable_type VARCHAR(30) NOT NULL,
    payable_ref_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    recipient_number VARCHAR(15),
    recipient_name VARCHAR(100),
    status VARCHAR(25) NOT NULL DEFAULT 'OUTSTANDING',
    description VARCHAR(255),
    due_date DATE,
    
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME,
    
    CONSTRAINT fk_payable_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_payable_member FOREIGN KEY (member_id) REFERENCES member(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_payable_tenant ON payable(tenant_id);
CREATE INDEX idx_payable_member ON payable(member_id);
CREATE INDEX idx_payable_type_ref ON payable(payable_type, payable_ref_id);
CREATE INDEX idx_payable_status ON payable(status);


-- =====================================================
-- Treasurer Device Table
-- Registered devices for SMS capture
-- =====================================================
CREATE TABLE IF NOT EXISTS treasurer_device (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    device_api_key VARCHAR(64) NOT NULL UNIQUE,
    device_name VARCHAR(100),
    phone_number VARCHAR(15),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    registered_at DATETIME NOT NULL,
    last_seen_at DATETIME,
    fcm_token VARCHAR(255),
    
    revoked_by_id BIGINT,
    revoked_at DATETIME,
    revocation_reason VARCHAR(255),
    
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME,
    
    CONSTRAINT fk_treasurer_device_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_treasurer_device_member FOREIGN KEY (member_id) REFERENCES member(id),
    CONSTRAINT fk_treasurer_device_revoked_by FOREIGN KEY (revoked_by_id) REFERENCES member(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_treasurer_device_member ON treasurer_device(member_id);
CREATE INDEX idx_treasurer_device_tenant ON treasurer_device(tenant_id);
CREATE UNIQUE INDEX idx_treasurer_device_api_key ON treasurer_device(device_api_key);


-- =====================================================
-- Payment Event Table
-- Incoming payment SMS captured from treasurer's device
-- =====================================================
CREATE TABLE IF NOT EXISTS payment_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    treasurer_device_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    payer_number VARCHAR(15) NOT NULL,
    payer_name VARCHAR(100),
    amount DECIMAL(12,2) NOT NULL,
    reference_code VARCHAR(20) NOT NULL,
    raw_sms_text TEXT,
    captured_at DATETIME NOT NULL,
    
    is_matched BOOLEAN NOT NULL DEFAULT FALSE,
    matched_by_id BIGINT,
    matched_at DATETIME,
    
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME,
    
    CONSTRAINT fk_payment_event_device FOREIGN KEY (treasurer_device_id) REFERENCES treasurer_device(id),
    CONSTRAINT fk_payment_event_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_payment_event_matched_by FOREIGN KEY (matched_by_id) REFERENCES member(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_event_device ON payment_event(treasurer_device_id);
CREATE INDEX idx_event_tenant ON payment_event(tenant_id);
CREATE INDEX idx_event_reference ON payment_event(reference_code);
CREATE INDEX idx_event_payer ON payment_event(payer_number);
CREATE INDEX idx_event_captured ON payment_event(captured_at);


-- =====================================================
-- Payment Claim Table
-- Payment claims initiated by payers
-- =====================================================
CREATE TABLE IF NOT EXISTS payment_claim (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    payable_id BIGINT NOT NULL,
    payer_id BIGINT NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    payment_method VARCHAR(20) NOT NULL,
    reference_code VARCHAR(20),
    payer_phone VARCHAR(15),
    captured_at DATETIME,
    
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_SMS',
    matched_at DATETIME,
    matched_event_id BIGINT,
    
    -- STK Push fields
    checkout_request_id VARCHAR(100),
    merchant_request_id VARCHAR(100),
    mpesa_receipt VARCHAR(20),
    
    error_message VARCHAR(500),
    expires_at DATETIME,
    
    -- Manual verification fields
    verified_by_id BIGINT,
    verified_at DATETIME,
    rejection_reason VARCHAR(500),
    
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME,
    
    CONSTRAINT fk_payment_claim_payable FOREIGN KEY (payable_id) REFERENCES payable(id),
    CONSTRAINT fk_payment_claim_payer FOREIGN KEY (payer_id) REFERENCES member(id),
    CONSTRAINT fk_payment_claim_matched_event FOREIGN KEY (matched_event_id) REFERENCES payment_event(id),
    CONSTRAINT fk_payment_claim_verified_by FOREIGN KEY (verified_by_id) REFERENCES member(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_claim_payable ON payment_claim(payable_id);
CREATE INDEX idx_claim_payer ON payment_claim(payer_id);
CREATE INDEX idx_claim_reference ON payment_claim(reference_code);
CREATE INDEX idx_claim_status ON payment_claim(status);
CREATE INDEX idx_claim_checkout ON payment_claim(checkout_request_id);


-- =====================================================
-- Add back-reference from payment_event to payment_claim
-- (for matched_claim relationship)
-- =====================================================
-- Note: The matched_claim_id is handled through payment_claim.matched_event_id
-- using a bidirectional OneToOne relationship


-- =====================================================
-- Comments for documentation
-- =====================================================

/*
Payment Flow:

1. IN_APP_SMS (Dual SMS Matching):
   - Member initiates payment -> creates PaymentClaim (PENDING_SMS)
   - Member sends M-PESA to treasurer
   - Member captures "sent to" SMS -> updates claim with reference (PENDING_MATCH)
   - Treasurer device captures "received from" SMS -> creates PaymentEvent
   - Matcher matches by reference code -> MATCHED
   - Fallback: matches by amount+phone+time if reference not matched in 15 mins

2. STK_PUSH (Safaricom Daraja):
   - Member initiates payment -> creates PaymentClaim (PENDING_SMS)
   - System sends STK Push request to Safaricom
   - User approves on phone
   - Safaricom sends callback -> MATCHED or FAILED

3. MANUAL (Admin Verification):
   - Member initiates payment -> creates PaymentClaim (PENDING_SMS)
   - Member pays externally and submits reference
   - Admin verifies and approves -> MATCHED

PayableTypes:
   - CONTRIBUTION: Monthly/periodic contribution
   - LOAN_REPAYMENT: Loan repayment installment
   - WELFARE_DUE: Welfare event contribution
   - MGR_CONTRIBUTION: Merry-go-round contribution
   - MGR_FINE: Merry-go-round fine
   - SHARE_PURCHASE: Share purchase
   - INVESTMENT_BUYIN: Investment buy-in
   - LICENSE_INVOICE: Platform license invoice
   - REGISTRATION_FEE: Member registration fee
   - PENALTY: Contribution penalty
   - LOAN_PENALTY: Loan penalty
*/
