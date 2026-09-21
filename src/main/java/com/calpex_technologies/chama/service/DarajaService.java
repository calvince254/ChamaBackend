package com.calpex_technologies.chama.service;

import com.calpex_technologies.chama.dto.StkCallbackRequest;
import com.calpex_technologies.chama.dto.StkPushResponse;
import com.calpex_technologies.chama.entity.*;
import com.calpex_technologies.chama.repository.PaymentClaimRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for Safaricom Daraja API integration.
 * Handles M-PESA STK Push (Lipa Na M-PESA Online) functionality.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DarajaService {

    private final PaymentSettingsService paymentSettingsService;
    private final PaymentClaimRepository paymentClaimRepository;
    private final PayableService payableService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // Daraja API endpoints
    private static final String SANDBOX_AUTH_URL = "https://sandbox.safaricom.co.ke/oauth/v1/generate?grant_type=client_credentials";
    private static final String PRODUCTION_AUTH_URL = "https://api.safaricom.co.ke/oauth/v1/generate?grant_type=client_credentials";
    private static final String SANDBOX_STK_URL = "https://sandbox.safaricom.co.ke/mpesa/stkpush/v1/processrequest";
    private static final String PRODUCTION_STK_URL = "https://api.safaricom.co.ke/mpesa/stkpush/v1/processrequest";
    private static final String SANDBOX_QUERY_URL = "https://sandbox.safaricom.co.ke/mpesa/stkpushquery/v1/query";
    private static final String PRODUCTION_QUERY_URL = "https://api.safaricom.co.ke/mpesa/stkpushquery/v1/query";

    // Default to sandbox for development
    private boolean useSandbox = true;

    /**
     * Initiate STK Push payment.
     * This sends a payment prompt to the user's phone.
     */
    @Transactional
    public StkPushResponse initiateStkPush(PaymentClaim claim, String phoneNumber) {
        Long tenantId = claim.getPayable().getTenant().getId();
        PaymentSettings settings = paymentSettingsService.getSettingsEntity(tenantId);

        if (!settings.isStkPushEnabled() || !isDarajaConfigured(settings)) {
            return StkPushResponse.builder()
                    .success(false)
                    .errorMessage("STK Push is not configured for this chama")
                    .build();
        }

        try {
            // Get access token
            String accessToken = getAccessToken(settings);
            if (accessToken == null) {
                return StkPushResponse.builder()
                        .success(false)
                        .errorMessage("Failed to authenticate with M-PESA")
                        .build();
            }

            // Normalize phone number
            String normalizedPhone = normalizePhoneNumber(phoneNumber);

            // Generate timestamp and password
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            String password = generatePassword(settings.getBusinessShortCode(), 
                    settings.getLipaNaMpesaPasskey(), timestamp);

            // Build request
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("BusinessShortCode", settings.getBusinessShortCode());
            requestBody.put("Password", password);
            requestBody.put("Timestamp", timestamp);
            requestBody.put("TransactionType", "CustomerPayBillOnline");
            requestBody.put("Amount", claim.getAmount().intValue());
            requestBody.put("PartyA", normalizedPhone);
            requestBody.put("PartyB", settings.getBusinessShortCode());
            requestBody.put("PhoneNumber", normalizedPhone);
            requestBody.put("CallBackURL", settings.getCallbackUrl());
            requestBody.put("AccountReference", settings.getAccountReference() != null 
                    ? settings.getAccountReference() 
                    : "Payment");
            requestBody.put("TransactionDesc", settings.getTransactionDesc() != null 
                    ? settings.getTransactionDesc() 
                    : "Payment for " + claim.getPayable().getDescription());

            // Make request
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            String stkUrl = useSandbox ? SANDBOX_STK_URL : PRODUCTION_STK_URL;

            ResponseEntity<String> response = restTemplate.postForEntity(stkUrl, request, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode jsonResponse = objectMapper.readTree(response.getBody());
                
                String responseCode = jsonResponse.path("ResponseCode").asText();
                if ("0".equals(responseCode)) {
                    String checkoutRequestId = jsonResponse.path("CheckoutRequestID").asText();
                    String merchantRequestId = jsonResponse.path("MerchantRequestID").asText();
                    String customerMessage = jsonResponse.path("CustomerMessage").asText();

                    // Update claim with checkout details
                    claim.setCheckoutRequestId(checkoutRequestId);
                    claim.setMerchantRequestId(merchantRequestId);
                    claim.setPayerPhone(normalizedPhone);
                    claim.setStatus(PaymentClaimStatus.PENDING_MATCH);
                    paymentClaimRepository.save(claim);

                    log.info("STK Push initiated: checkoutRequestId={}, phone={}", 
                            checkoutRequestId, normalizedPhone);

                    return StkPushResponse.builder()
                            .claimId(claim.getId())
                            .checkoutRequestId(checkoutRequestId)
                            .merchantRequestId(merchantRequestId)
                            .customerMessage(customerMessage)
                            .responseDescription(jsonResponse.path("ResponseDescription").asText())
                            .success(true)
                            .build();
                } else {
                    String errorDesc = jsonResponse.path("ResponseDescription").asText();
                    log.error("STK Push failed: {}", errorDesc);
                    
                    claim.setStatus(PaymentClaimStatus.FAILED);
                    claim.setErrorMessage(errorDesc);
                    paymentClaimRepository.save(claim);

                    return StkPushResponse.builder()
                            .success(false)
                            .errorMessage(errorDesc)
                            .build();
                }
            } else {
                log.error("STK Push request failed with status: {}", response.getStatusCode());
                return StkPushResponse.builder()
                        .success(false)
                        .errorMessage("Failed to initiate payment")
                        .build();
            }

        } catch (Exception e) {
            log.error("Error initiating STK Push: {}", e.getMessage(), e);
            return StkPushResponse.builder()
                    .success(false)
                    .errorMessage("Error: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Process STK Push callback from Safaricom.
     * This is called by Safaricom after the user completes or cancels payment.
     */
    @Transactional
    public void processCallback(StkCallbackRequest callback) {
        String checkoutRequestId = callback.getCheckoutRequestId();
        
        log.info("Processing STK callback for checkoutRequestId: {}", checkoutRequestId);

        PaymentClaim claim = paymentClaimRepository.findByCheckoutRequestId(checkoutRequestId)
                .orElse(null);

        if (claim == null) {
            log.warn("No payment claim found for checkoutRequestId: {}", checkoutRequestId);
            return;
        }

        if (callback.isSuccessful()) {
            // Payment successful
            String mpesaReceipt = callback.getMpesaReceiptNumber();
            
            claim.setStatus(PaymentClaimStatus.MATCHED);
            claim.setMpesaReceipt(mpesaReceipt);
            claim.setReferenceCode(mpesaReceipt);
            claim.setMatchedAt(LocalDateTime.now());
            paymentClaimRepository.save(claim);

            // Mark payable as paid
            payableService.markAsPaid(claim.getPayable().getId());

            log.info("STK Push payment successful: claim={}, receipt={}", 
                    claim.getId(), mpesaReceipt);

        } else {
            // Payment failed or cancelled
            String resultDesc = callback.getResultDesc();
            
            claim.setStatus(PaymentClaimStatus.FAILED);
            claim.setErrorMessage(resultDesc);
            paymentClaimRepository.save(claim);

            // Revert payable to outstanding
            payableService.markExpired(claim.getPayable().getId());

            log.info("STK Push payment failed/cancelled: claim={}, reason={}", 
                    claim.getId(), resultDesc);
        }
    }

    /**
     * Query STK Push transaction status.
     * Used to check if a transaction completed when callback wasn't received.
     */
    public Map<String, Object> queryTransactionStatus(PaymentClaim claim) {
        Long tenantId = claim.getPayable().getTenant().getId();
        PaymentSettings settings = paymentSettingsService.getSettingsEntity(tenantId);

        Map<String, Object> result = new HashMap<>();

        try {
            String accessToken = getAccessToken(settings);
            if (accessToken == null) {
                result.put("success", false);
                result.put("error", "Failed to authenticate");
                return result;
            }

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            String password = generatePassword(settings.getBusinessShortCode(), 
                    settings.getLipaNaMpesaPasskey(), timestamp);

            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("BusinessShortCode", settings.getBusinessShortCode());
            requestBody.put("Password", password);
            requestBody.put("Timestamp", timestamp);
            requestBody.put("CheckoutRequestID", claim.getCheckoutRequestId());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);

            HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);
            String queryUrl = useSandbox ? SANDBOX_QUERY_URL : PRODUCTION_QUERY_URL;

            ResponseEntity<String> response = restTemplate.postForEntity(queryUrl, request, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode jsonResponse = objectMapper.readTree(response.getBody());
                
                String resultCode = jsonResponse.path("ResultCode").asText();
                result.put("success", true);
                result.put("resultCode", resultCode);
                result.put("resultDesc", jsonResponse.path("ResultDesc").asText());

                // Process based on result code
                if ("0".equals(resultCode)) {
                    // Transaction completed successfully
                    result.put("status", "COMPLETED");
                } else if ("1032".equals(resultCode)) {
                    // Transaction cancelled by user
                    result.put("status", "CANCELLED");
                } else if ("1037".equals(resultCode)) {
                    // Timeout - still waiting
                    result.put("status", "PENDING");
                } else {
                    result.put("status", "FAILED");
                }
            }

        } catch (Exception e) {
            log.error("Error querying transaction status: {}", e.getMessage());
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * Get OAuth access token from Safaricom.
     */
    private String getAccessToken(PaymentSettings settings) {
        try {
            String credentials = settings.getDarajaConsumerKey() + ":" + settings.getDarajaConsumerSecret();
            String encodedCredentials = Base64.getEncoder().encodeToString(
                    credentials.getBytes(StandardCharsets.UTF_8));

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Basic " + encodedCredentials);

            HttpEntity<String> request = new HttpEntity<>(headers);
            String authUrl = useSandbox ? SANDBOX_AUTH_URL : PRODUCTION_AUTH_URL;

            ResponseEntity<String> response = restTemplate.exchange(
                    authUrl, HttpMethod.GET, request, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode jsonResponse = objectMapper.readTree(response.getBody());
                return jsonResponse.path("access_token").asText();
            }

        } catch (Exception e) {
            log.error("Error getting Daraja access token: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Generate password for STK Push request.
     */
    private String generatePassword(String shortCode, String passkey, String timestamp) {
        String str = shortCode + passkey + timestamp;
        return Base64.getEncoder().encodeToString(str.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Normalize phone number to 254XXXXXXXXX format.
     */
    private String normalizePhoneNumber(String phone) {
        if (phone == null) return null;
        phone = phone.replaceAll("[\\s\\-()]+", "");
        if (phone.startsWith("+")) phone = phone.substring(1);
        if (phone.startsWith("0")) phone = "254" + phone.substring(1);
        if (phone.length() == 9) phone = "254" + phone;
        return phone;
    }

    /**
     * Check if Daraja is properly configured.
     */
    private boolean isDarajaConfigured(PaymentSettings settings) {
        return settings.getDarajaConsumerKey() != null && !settings.getDarajaConsumerKey().isBlank()
                && settings.getDarajaConsumerSecret() != null && !settings.getDarajaConsumerSecret().isBlank()
                && settings.getBusinessShortCode() != null && !settings.getBusinessShortCode().isBlank()
                && settings.getLipaNaMpesaPasskey() != null && !settings.getLipaNaMpesaPasskey().isBlank()
                && settings.getCallbackUrl() != null && !settings.getCallbackUrl().isBlank();
    }

    /**
     * Set whether to use sandbox or production API.
     */
    public void setUseSandbox(boolean useSandbox) {
        this.useSandbox = useSandbox;
        log.info("Daraja API mode set to: {}", useSandbox ? "SANDBOX" : "PRODUCTION");
    }
}
