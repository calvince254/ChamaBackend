package com.calpex_technologies.chama.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Safaricom STK Push callback request structure.
 * This is the structure Safaricom sends to our callback URL after payment.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StkCallbackRequest {
    
    @JsonProperty("Body")
    private Body body;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Body {
        @JsonProperty("stkCallback")
        private StkCallback stkCallback;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StkCallback {
        @JsonProperty("MerchantRequestID")
        private String merchantRequestId;
        
        @JsonProperty("CheckoutRequestID")
        private String checkoutRequestId;
        
        @JsonProperty("ResultCode")
        private int resultCode;
        
        @JsonProperty("ResultDesc")
        private String resultDesc;
        
        @JsonProperty("CallbackMetadata")
        private CallbackMetadata callbackMetadata;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CallbackMetadata {
        @JsonProperty("Item")
        private List<Item> items;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        @JsonProperty("Name")
        private String name;
        
        @JsonProperty("Value")
        private Object value;
    }
    
    /**
     * Helper method to get M-PESA receipt number from callback metadata.
     */
    public String getMpesaReceiptNumber() {
        if (body != null && body.stkCallback != null && 
            body.stkCallback.callbackMetadata != null && 
            body.stkCallback.callbackMetadata.items != null) {
            for (Item item : body.stkCallback.callbackMetadata.items) {
                if ("MpesaReceiptNumber".equals(item.name)) {
                    return String.valueOf(item.value);
                }
            }
        }
        return null;
    }
    
    /**
     * Helper method to get transaction amount from callback metadata.
     */
    public Double getAmount() {
        if (body != null && body.stkCallback != null && 
            body.stkCallback.callbackMetadata != null && 
            body.stkCallback.callbackMetadata.items != null) {
            for (Item item : body.stkCallback.callbackMetadata.items) {
                if ("Amount".equals(item.name)) {
                    return ((Number) item.value).doubleValue();
                }
            }
        }
        return null;
    }
    
    /**
     * Helper method to get phone number from callback metadata.
     */
    public String getPhoneNumber() {
        if (body != null && body.stkCallback != null && 
            body.stkCallback.callbackMetadata != null && 
            body.stkCallback.callbackMetadata.items != null) {
            for (Item item : body.stkCallback.callbackMetadata.items) {
                if ("PhoneNumber".equals(item.name)) {
                    return String.valueOf(item.value);
                }
            }
        }
        return null;
    }
    
    /**
     * Check if the payment was successful.
     */
    public boolean isSuccessful() {
        return body != null && body.stkCallback != null && body.stkCallback.resultCode == 0;
    }
    
    /**
     * Get the checkout request ID.
     */
    public String getCheckoutRequestId() {
        if (body != null && body.stkCallback != null) {
            return body.stkCallback.checkoutRequestId;
        }
        return null;
    }
    
    /**
     * Get the result description.
     */
    public String getResultDesc() {
        if (body != null && body.stkCallback != null) {
            return body.stkCallback.resultDesc;
        }
        return null;
    }
}
