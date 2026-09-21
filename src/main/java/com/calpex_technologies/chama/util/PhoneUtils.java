package com.calpex_technologies.chama.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Utility class for phone number operations.
 * Handles various Kenyan phone number formats and normalization.
 */
public class PhoneUtils {

    // Regex to detect if input looks like a phone number (starts with digit, +, or has mostly digits)
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[+]?[0-9\\s\\-()]+$");
    
    // Email pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    /**
     * Check if the input looks like a phone number rather than an email.
     */
    public static boolean isPhoneNumber(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }
        String cleaned = input.trim();
        
        // If it contains @, it's definitely an email
        if (cleaned.contains("@")) {
            return false;
        }
        
        // Check if it matches phone pattern (digits, +, spaces, dashes)
        return PHONE_PATTERN.matcher(cleaned).matches();
    }

    /**
     * Check if the input looks like an email.
     */
    public static boolean isEmail(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }
        return EMAIL_PATTERN.matcher(input.trim()).matches();
    }

    /**
     * Normalize a phone number to standard format (254XXXXXXXXX without +).
     * Handles various input formats:
     * - 0712345678 -> 254712345678
     * - +254712345678 -> 254712345678
     * - 254712345678 -> 254712345678
     * - 712345678 -> 254712345678
     */
    public static String normalize(String phone) {
        if (phone == null || phone.isBlank()) {
            return phone;
        }
        
        // Remove all non-digit characters
        String digits = phone.replaceAll("[^0-9]", "");
        
        if (digits.isEmpty()) {
            return phone;
        }
        
        // Handle different formats
        if (digits.startsWith("254")) {
            // Already in international format
            return digits;
        } else if (digits.startsWith("0") && digits.length() >= 10) {
            // Local format: 0712345678 -> 254712345678
            return "254" + digits.substring(1);
        } else if (digits.length() == 9) {
            // Short format without leading 0: 712345678 -> 254712345678
            return "254" + digits;
        }
        
        // Return as-is if we can't normalize
        return digits;
    }

    /**
     * Generate all possible phone format variations for searching.
     * This allows finding a user regardless of how their phone was stored.
     * 
     * Input: Any phone format
     * Output: List of possible formats the phone could be stored as
     */
    public static List<String> getPhoneVariants(String phone) {
        List<String> variants = new ArrayList<>();
        
        if (phone == null || phone.isBlank()) {
            return variants;
        }
        
        // Remove all non-digit characters except +
        String cleaned = phone.trim();
        String digits = phone.replaceAll("[^0-9]", "");
        
        if (digits.isEmpty()) {
            variants.add(cleaned);
            return variants;
        }
        
        // Normalize to get the base number (254XXXXXXXXX)
        String normalized = normalize(phone);
        
        // Generate all variants
        if (normalized.startsWith("254") && normalized.length() >= 12) {
            String localPart = normalized.substring(3); // Remove 254
            
            // International format without +
            variants.add(normalized);                    // 254712345678
            // International format with +
            variants.add("+" + normalized);              // +254712345678
            // Local format with 0
            variants.add("0" + localPart);               // 0712345678
            // Short format
            variants.add(localPart);                     // 712345678
            // With spaces (common formats)
            variants.add("+254 " + localPart.substring(0, 3) + " " + localPart.substring(3)); // +254 712 345678
            variants.add("0" + localPart.substring(0, 3) + " " + localPart.substring(3));      // 0712 345678
        } else {
            // Can't normalize - just use the original
            variants.add(cleaned);
            variants.add(digits);
        }
        
        return variants;
    }

    /**
     * Format phone for display (to +254 XXX XXXXXX format)
     */
    public static String formatForDisplay(String phone) {
        String normalized = normalize(phone);
        if (normalized != null && normalized.startsWith("254") && normalized.length() == 12) {
            return "+" + normalized.substring(0, 3) + " " + 
                   normalized.substring(3, 6) + " " + 
                   normalized.substring(6);
        }
        return phone;
    }
}
