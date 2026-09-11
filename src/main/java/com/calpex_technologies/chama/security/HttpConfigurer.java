package com.calpex_technologies.chama.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.stereotype.Component;

/**
 * Configures HTTP security matchers with required authorities for all endpoints.
 * 
 * Permission format: SNAKE_CASE endpoint names e.g. VIEW_MEMBERS, APPLY_FOR_LOAN, DISBURSE_LOAN
 * Each permission corresponds to a specific endpoint/action in the system.
 * 
 * Super admin gets "FULL_ACCESS" which grants access to everything.
 */
@Component
public class HttpConfigurer {

    /**
     * Configure all endpoint matchers with required authorities
     */
    public void configure(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
            // ==================== PUBLIC ENDPOINTS ====================
            .requestMatchers("/api/auth/**").permitAll()
            .requestMatchers("/api/permissions").hasAnyAuthority("FULL_ACCESS", "VIEW_ROLES", "MANAGE_ROLE_PERMISSIONS")  // Get available permissions for roles UI
            .requestMatchers("/h2-console/**").permitAll()
            
            // ==================== MEMBERS ====================
            .requestMatchers("GET", "/api/members", "/api/members/active").hasAnyAuthority("FULL_ACCESS", "VIEW_MEMBERS")
            .requestMatchers("GET", "/api/members/{id}").hasAnyAuthority("FULL_ACCESS", "VIEW_MEMBER_DETAILS")
            .requestMatchers("POST", "/api/members").hasAnyAuthority("FULL_ACCESS", "CREATE_MEMBER")
            .requestMatchers("PUT", "/api/members/{id}").hasAnyAuthority("FULL_ACCESS", "EDIT_MEMBER")
            .requestMatchers("DELETE", "/api/members/{id}").hasAnyAuthority("FULL_ACCESS", "DELETE_MEMBER")
            .requestMatchers("PATCH", "/api/members/{id}/deactivate").hasAnyAuthority("FULL_ACCESS", "DEACTIVATE_MEMBER")
            .requestMatchers("PATCH", "/api/members/{id}/activate").hasAnyAuthority("FULL_ACCESS", "ACTIVATE_MEMBER")
            
            // ==================== CONTRIBUTIONS ====================
            .requestMatchers("GET", "/api/contributions/**").hasAnyAuthority("FULL_ACCESS", "VIEW_CONTRIBUTIONS")
            .requestMatchers("POST", "/api/contributions").hasAnyAuthority("FULL_ACCESS", "RECORD_CONTRIBUTION")
            .requestMatchers("POST", "/api/contributions/bulk").hasAnyAuthority("FULL_ACCESS", "BULK_RECORD_CONTRIBUTIONS")
            .requestMatchers("PUT", "/api/contributions/{id}").hasAnyAuthority("FULL_ACCESS", "EDIT_CONTRIBUTION")
            .requestMatchers("DELETE", "/api/contributions/{id}").hasAnyAuthority("FULL_ACCESS", "DELETE_CONTRIBUTION")
            .requestMatchers("PATCH", "/api/contributions/{id}/approve").hasAnyAuthority("FULL_ACCESS", "APPROVE_CONTRIBUTION")
            
            // ==================== CONTRIBUTION TYPES (Settings) ====================
            .requestMatchers("GET", "/api/contribution-types/**").hasAnyAuthority("FULL_ACCESS", "VIEW_CONTRIBUTION_TYPES")
            .requestMatchers("POST", "/api/contribution-types").hasAnyAuthority("FULL_ACCESS", "CREATE_CONTRIBUTION_TYPE")
            .requestMatchers("PUT", "/api/contribution-types/{id}").hasAnyAuthority("FULL_ACCESS", "EDIT_CONTRIBUTION_TYPE")
            .requestMatchers("DELETE", "/api/contribution-types/{id}").hasAnyAuthority("FULL_ACCESS", "DELETE_CONTRIBUTION_TYPE")
            
            // ==================== LOANS ====================
            .requestMatchers("GET", "/api/loans/my-loans").authenticated()  // Any authenticated user can see their own loans
            .requestMatchers("GET", "/api/loans/pending-approvals").hasAnyAuthority("FULL_ACCESS", "VIEW_PENDING_LOAN_APPROVALS")
            .requestMatchers("GET", "/api/loans", "/api/loans/{id}").hasAnyAuthority("FULL_ACCESS", "VIEW_LOANS")
            .requestMatchers("POST", "/api/loans", "/api/loans/apply").hasAnyAuthority("FULL_ACCESS", "APPLY_FOR_LOAN")
            .requestMatchers("PUT", "/api/loans/{id}").hasAnyAuthority("FULL_ACCESS", "EDIT_LOAN")
            .requestMatchers("DELETE", "/api/loans/{id}").hasAnyAuthority("FULL_ACCESS", "DELETE_LOAN")
            .requestMatchers("POST", "/api/loans/{id}/approve").hasAnyAuthority("FULL_ACCESS", "APPROVE_LOAN")
            .requestMatchers("POST", "/api/loans/{id}/reject").hasAnyAuthority("FULL_ACCESS", "REJECT_LOAN")
            .requestMatchers("POST", "/api/loans/{id}/disburse").hasAnyAuthority("FULL_ACCESS", "DISBURSE_LOAN")
            .requestMatchers("POST", "/api/loans/{id}/repay").hasAnyAuthority("FULL_ACCESS", "RECORD_LOAN_REPAYMENT")
            
            // ==================== LOAN PRODUCTS (Settings) ====================
            .requestMatchers("GET", "/api/loan-products/**").hasAnyAuthority("FULL_ACCESS", "VIEW_LOAN_PRODUCTS")
            .requestMatchers("POST", "/api/loan-products").hasAnyAuthority("FULL_ACCESS", "CREATE_LOAN_PRODUCT")
            .requestMatchers("PUT", "/api/loan-products/{id}").hasAnyAuthority("FULL_ACCESS", "EDIT_LOAN_PRODUCT")
            .requestMatchers("DELETE", "/api/loan-products/{id}").hasAnyAuthority("FULL_ACCESS", "DELETE_LOAN_PRODUCT")
            
            // ==================== LOAN PENALTIES ====================
            .requestMatchers("GET", "/api/loans/penalties/my-penalties").authenticated()
            .requestMatchers("GET", "/api/loans/penalties/**").hasAnyAuthority("FULL_ACCESS", "VIEW_LOAN_PENALTIES")
            .requestMatchers("POST", "/api/loans/penalties/{id}/waive").hasAnyAuthority("FULL_ACCESS", "WAIVE_LOAN_PENALTY")
            .requestMatchers("POST", "/api/loans/penalties/{id}/pay").hasAnyAuthority("FULL_ACCESS", "PAY_LOAN_PENALTY")
            
            // ==================== MEETINGS ====================
            .requestMatchers("GET", "/api/meetings/**").hasAnyAuthority("FULL_ACCESS", "VIEW_MEETINGS")
            .requestMatchers("POST", "/api/meetings").hasAnyAuthority("FULL_ACCESS", "CREATE_MEETING")
            .requestMatchers("PUT", "/api/meetings/{id}").hasAnyAuthority("FULL_ACCESS", "EDIT_MEETING")
            .requestMatchers("DELETE", "/api/meetings/{id}").hasAnyAuthority("FULL_ACCESS", "DELETE_MEETING")
            .requestMatchers("POST", "/api/meetings/{id}/attendance/**").hasAnyAuthority("FULL_ACCESS", "RECORD_MEETING_ATTENDANCE")
            .requestMatchers("POST", "/api/meetings/{id}/rsvp").authenticated()  // Any member can RSVP
            // Meeting Minutes
            .requestMatchers("POST", "/api/meetings/{id}/minutes/upload").hasAnyAuthority("FULL_ACCESS", "UPLOAD_MEETING_MINUTES")
            .requestMatchers("GET", "/api/meetings/{id}/minutes/download").hasAnyAuthority("FULL_ACCESS", "DOWNLOAD_MEETING_MINUTES")
            .requestMatchers("GET", "/api/meetings/{id}/minutes/view").hasAnyAuthority("FULL_ACCESS", "VIEW_MEETING_MINUTES")
            .requestMatchers("GET", "/api/meetings/{id}/minutes/generate").hasAnyAuthority("FULL_ACCESS", "VIEW_MEETINGS")  // Anyone who can view meetings can generate template
            .requestMatchers("DELETE", "/api/meetings/{id}/minutes").hasAnyAuthority("FULL_ACCESS", "DELETE_MEETING_MINUTES")
            .requestMatchers("POST", "/api/meetings/{id}/minutes/confirm").hasAnyAuthority("FULL_ACCESS", "CONFIRM_MEETING_MINUTES")
            
            // ==================== WELFARE ====================
            .requestMatchers("GET", "/api/welfare/**").hasAnyAuthority("FULL_ACCESS", "VIEW_WELFARE_EVENTS")
            .requestMatchers("POST", "/api/welfare").hasAnyAuthority("FULL_ACCESS", "CREATE_WELFARE_EVENT")
            .requestMatchers("PUT", "/api/welfare/{id}").hasAnyAuthority("FULL_ACCESS", "EDIT_WELFARE_EVENT")
            .requestMatchers("DELETE", "/api/welfare/{id}").hasAnyAuthority("FULL_ACCESS", "DELETE_WELFARE_EVENT")
            .requestMatchers("POST", "/api/welfare/{id}/approve").hasAnyAuthority("FULL_ACCESS", "APPROVE_WELFARE_CLAIM")
            .requestMatchers("POST", "/api/welfare/{id}/disburse").hasAnyAuthority("FULL_ACCESS", "DISBURSE_WELFARE")
            .requestMatchers("PUT", "/api/welfare/{id}/contributions/**").hasAnyAuthority("FULL_ACCESS", "MANAGE_WELFARE_CONTRIBUTIONS")
            .requestMatchers("POST", "/api/welfare/{id}/contributions/**").hasAnyAuthority("FULL_ACCESS", "MANAGE_WELFARE_CONTRIBUTIONS")
            
            // ==================== FINANCE ====================
            .requestMatchers("GET", "/api/finance/**").hasAnyAuthority("FULL_ACCESS", "VIEW_FINANCE_SUMMARY")
            .requestMatchers("GET", "/api/finance/transactions/**").hasAnyAuthority("FULL_ACCESS", "VIEW_TRANSACTIONS")
            .requestMatchers("POST", "/api/finance/transactions").hasAnyAuthority("FULL_ACCESS", "RECORD_TRANSACTION")
            .requestMatchers("PUT", "/api/finance/transactions/{id}").hasAnyAuthority("FULL_ACCESS", "EDIT_TRANSACTION")
            .requestMatchers("DELETE", "/api/finance/transactions/{id}").hasAnyAuthority("FULL_ACCESS", "DELETE_TRANSACTION")
            
            // ==================== SHARES ====================
            .requestMatchers("GET", "/api/shares/**").hasAnyAuthority("FULL_ACCESS", "VIEW_SHARES")
            .requestMatchers("POST", "/api/shares").hasAnyAuthority("FULL_ACCESS", "RECORD_SHARE_PURCHASE")
            .requestMatchers("PUT", "/api/shares/{id}").hasAnyAuthority("FULL_ACCESS", "EDIT_SHARE_ENTRY")
            .requestMatchers("DELETE", "/api/shares/{id}").hasAnyAuthority("FULL_ACCESS", "DELETE_SHARE_ENTRY")
            .requestMatchers("GET", "/api/shares/settings").hasAnyAuthority("FULL_ACCESS", "VIEW_SHARE_SETTINGS")
            .requestMatchers("PUT", "/api/shares/settings").hasAnyAuthority("FULL_ACCESS", "MANAGE_SHARE_SETTINGS")
            
            // ==================== PAYOUTS ====================
            .requestMatchers("GET", "/api/payouts/liquidity").hasAnyAuthority("FULL_ACCESS", "VIEW_PAYOUTS")
            .requestMatchers("POST", "/api/payouts/preview").hasAnyAuthority("FULL_ACCESS", "VIEW_PAYOUTS", "CREATE_PAYOUT")
            .requestMatchers("GET", "/api/payouts", "/api/payouts/{id}").hasAnyAuthority("FULL_ACCESS", "VIEW_PAYOUTS")
            .requestMatchers("POST", "/api/payouts").hasAnyAuthority("FULL_ACCESS", "CREATE_PAYOUT")
            .requestMatchers("POST", "/api/payouts/{id}/submit").hasAnyAuthority("FULL_ACCESS", "CREATE_PAYOUT")
            .requestMatchers("POST", "/api/payouts/{id}/approve").hasAnyAuthority("FULL_ACCESS", "APPROVE_PAYOUT")
            .requestMatchers("POST", "/api/payouts/{id}/cancel").hasAnyAuthority("FULL_ACCESS", "CANCEL_PAYOUT")
            .requestMatchers("POST", "/api/payouts/{id}/members/{memberId}/paid").hasAnyAuthority("FULL_ACCESS", "PROCESS_PAYOUT")
            .requestMatchers("POST", "/api/payouts/{id}/mark-all-paid").hasAnyAuthority("FULL_ACCESS", "PROCESS_PAYOUT")
            
            // ==================== INVESTMENTS ====================
            .requestMatchers("GET", "/api/investments/**").hasAnyAuthority("FULL_ACCESS", "VIEW_INVESTMENTS")
            .requestMatchers("POST", "/api/investments").hasAnyAuthority("FULL_ACCESS", "CREATE_INVESTMENT")
            .requestMatchers("PUT", "/api/investments/{id}").hasAnyAuthority("FULL_ACCESS", "EDIT_INVESTMENT")
            .requestMatchers("DELETE", "/api/investments/{id}").hasAnyAuthority("FULL_ACCESS", "DELETE_INVESTMENT")
            .requestMatchers("POST", "/api/investments/{id}/close").hasAnyAuthority("FULL_ACCESS", "CLOSE_INVESTMENT")
            
            // ==================== MERRY-GO-ROUND ====================
            .requestMatchers("GET", "/api/merry-go-round/**").hasAnyAuthority("FULL_ACCESS", "VIEW_MERRY_GO_ROUND")
            .requestMatchers("POST", "/api/merry-go-round/cycles").hasAnyAuthority("FULL_ACCESS", "CREATE_MGR_CYCLE")
            .requestMatchers("PUT", "/api/merry-go-round/cycles/{id}").hasAnyAuthority("FULL_ACCESS", "EDIT_MGR_CYCLE")
            .requestMatchers("DELETE", "/api/merry-go-round/cycles/{id}").hasAnyAuthority("FULL_ACCESS", "DELETE_MGR_CYCLE")
            // Participants
            .requestMatchers("POST", "/api/merry-go-round/cycles/{id}/participants/**").hasAnyAuthority("FULL_ACCESS", "EDIT_MGR_CYCLE")
            .requestMatchers("DELETE", "/api/merry-go-round/cycles/{id}/participants/**").hasAnyAuthority("FULL_ACCESS", "EDIT_MGR_CYCLE")
            .requestMatchers("PUT", "/api/merry-go-round/cycles/{id}/participants/**").hasAnyAuthority("FULL_ACCESS", "EDIT_MGR_CYCLE")
            // Rounds
            .requestMatchers("POST", "/api/merry-go-round/rounds/{id}/pay").hasAnyAuthority("FULL_ACCESS", "COMPLETE_MGR_ROUND")
            // Contributions
            .requestMatchers("POST", "/api/merry-go-round/contributions/**").hasAnyAuthority("FULL_ACCESS", "RECORD_MGR_CONTRIBUTION")
            // Fines
            .requestMatchers("POST", "/api/merry-go-round/fines").hasAnyAuthority("FULL_ACCESS", "IMPOSE_MGR_FINE")
            .requestMatchers("POST", "/api/merry-go-round/fines/{id}/pay").hasAnyAuthority("FULL_ACCESS", "PAY_MGR_FINE")
            .requestMatchers("POST", "/api/merry-go-round/fines/{id}/waive").hasAnyAuthority("FULL_ACCESS", "WAIVE_MGR_FINE")
            
            // ==================== FINES ====================
            .requestMatchers("GET", "/api/fines/**").hasAnyAuthority("FULL_ACCESS", "VIEW_FINES")
            .requestMatchers("POST", "/api/fines").hasAnyAuthority("FULL_ACCESS", "RECORD_FINE")
            .requestMatchers("PUT", "/api/fines/{id}").hasAnyAuthority("FULL_ACCESS", "EDIT_FINE")
            .requestMatchers("DELETE", "/api/fines/{id}").hasAnyAuthority("FULL_ACCESS", "DELETE_FINE")
            .requestMatchers("POST", "/api/fines/{id}/waive").hasAnyAuthority("FULL_ACCESS", "WAIVE_FINE")
            
            // ==================== FINE TYPES (Settings) ====================
            .requestMatchers("GET", "/api/fine-types/**").hasAnyAuthority("FULL_ACCESS", "VIEW_FINE_TYPES")
            .requestMatchers("POST", "/api/fine-types").hasAnyAuthority("FULL_ACCESS", "CREATE_FINE_TYPE")
            .requestMatchers("PUT", "/api/fine-types/{id}").hasAnyAuthority("FULL_ACCESS", "EDIT_FINE_TYPE")
            .requestMatchers("DELETE", "/api/fine-types/{id}").hasAnyAuthority("FULL_ACCESS", "DELETE_FINE_TYPE")
            
            // ==================== PENALTIES ====================
            .requestMatchers("GET", "/api/penalties/**").hasAnyAuthority("FULL_ACCESS", "VIEW_PENALTIES")
            .requestMatchers("POST", "/api/penalties").hasAnyAuthority("FULL_ACCESS", "RECORD_PENALTY")
            .requestMatchers("PUT", "/api/penalties/{id}").hasAnyAuthority("FULL_ACCESS", "EDIT_PENALTY")
            .requestMatchers("DELETE", "/api/penalties/{id}").hasAnyAuthority("FULL_ACCESS", "DELETE_PENALTY")
            
            // ==================== ROLES ====================
            .requestMatchers("GET", "/api/roles/**").hasAnyAuthority("FULL_ACCESS", "VIEW_ROLES")
            .requestMatchers("POST", "/api/roles").hasAnyAuthority("FULL_ACCESS", "CREATE_ROLE")
            .requestMatchers("PUT", "/api/roles/{id}").hasAnyAuthority("FULL_ACCESS", "EDIT_ROLE")
            .requestMatchers("DELETE", "/api/roles/{id}").hasAnyAuthority("FULL_ACCESS", "DELETE_ROLE")
            .requestMatchers("PUT", "/api/roles/{id}/permissions").hasAnyAuthority("FULL_ACCESS", "MANAGE_ROLE_PERMISSIONS")
            
            // ==================== TENANTS (Super Admin only for most operations) ====================
            // Regular users can access their own tenant settings
            .requestMatchers("GET", "/api/tenants/settings").hasAnyAuthority("FULL_ACCESS", "VIEW_TENANT_SETTINGS")
            .requestMatchers("PUT", "/api/tenants/settings").hasAnyAuthority("FULL_ACCESS", "EDIT_TENANT_SETTINGS")
            // Super admin operations for specific tenants
            .requestMatchers("/api/tenants/**").hasAuthority("FULL_ACCESS")
            
            // ==================== SYSTEM SETTINGS (Super Admin only) ====================
            .requestMatchers("/api/system-settings/**").hasAuthority("FULL_ACCESS")
            
            // ==================== DASHBOARD ====================
            .requestMatchers("GET", "/api/dashboard/**").hasAnyAuthority("FULL_ACCESS", "VIEW_DASHBOARD")
            
            // ==================== DEFAULT ====================
            .anyRequest().authenticated()
        );
    }
    
    /**
     * Get all available permissions grouped by module for the Roles & Rights UI.
     * This is used by the frontend to display all available permissions.
     */
    public static java.util.Map<String, java.util.List<PermissionDefinition>> getAllPermissions() {
        java.util.Map<String, java.util.List<PermissionDefinition>> permissions = new java.util.LinkedHashMap<>();
        
        // Dashboard
        permissions.put("Dashboard", java.util.List.of(
            new PermissionDefinition("VIEW_DASHBOARD", "View Dashboard")
        ));
        
        // Members
        permissions.put("Members", java.util.List.of(
            new PermissionDefinition("VIEW_MEMBERS", "View Members List"),
            new PermissionDefinition("VIEW_MEMBER_DETAILS", "View Member Details"),
            new PermissionDefinition("CREATE_MEMBER", "Create Member"),
            new PermissionDefinition("EDIT_MEMBER", "Edit Member"),
            new PermissionDefinition("DELETE_MEMBER", "Delete Member"),
            new PermissionDefinition("ACTIVATE_MEMBER", "Activate Member"),
            new PermissionDefinition("DEACTIVATE_MEMBER", "Deactivate Member")
        ));
        
        // Contributions
        permissions.put("Contributions", java.util.List.of(
            new PermissionDefinition("VIEW_CONTRIBUTIONS", "View Contributions"),
            new PermissionDefinition("GENERATE_CONTRIBUTIONS", "Generate Contributions"),
            new PermissionDefinition("RECORD_CONTRIBUTION_PAYMENT", "Record Contribution Payment"),
            new PermissionDefinition("RECORD_CONTRIBUTION", "Record Contribution"),
            new PermissionDefinition("BULK_RECORD_CONTRIBUTIONS", "Bulk Record Contributions"),
            new PermissionDefinition("EDIT_CONTRIBUTION", "Edit Contribution"),
            new PermissionDefinition("DELETE_CONTRIBUTION", "Delete Contribution"),
            new PermissionDefinition("APPROVE_CONTRIBUTION", "Approve Contribution"),
            new PermissionDefinition("CHECK_LATE_PAYMENTS", "Check Late Payments"),
            new PermissionDefinition("WAIVE_PENALTY", "Waive Penalty")
        ));
        
        // Loans
        permissions.put("Loans", java.util.List.of(
            new PermissionDefinition("VIEW_LOANS", "View All Loans"),
            new PermissionDefinition("VIEW_ALL_LOANS", "View All Loans (Admin)"),
            new PermissionDefinition("VIEW_PENDING_LOAN_APPROVALS", "View Pending Approvals"),
            new PermissionDefinition("APPLY_FOR_LOAN", "Apply for Loan"),
            new PermissionDefinition("EDIT_LOAN", "Edit Loan"),
            new PermissionDefinition("DELETE_LOAN", "Delete Loan"),
            new PermissionDefinition("APPROVE_LOAN", "Approve Loan"),
            new PermissionDefinition("REJECT_LOAN", "Reject Loan"),
            new PermissionDefinition("DISBURSE_LOAN", "Disburse Loan"),
            new PermissionDefinition("RECORD_LOAN_REPAYMENT", "Record Loan Repayment"),
            new PermissionDefinition("VIEW_LOAN_PENALTIES", "View Loan Penalties"),
            new PermissionDefinition("WAIVE_LOAN_PENALTY", "Waive Loan Penalty"),
            new PermissionDefinition("PAY_LOAN_PENALTY", "Pay Loan Penalty")
        ));
        
        // Meetings
        permissions.put("Meetings", java.util.List.of(
            new PermissionDefinition("VIEW_MEETINGS", "View Meetings"),
            new PermissionDefinition("SCHEDULE_MEETING", "Schedule Meeting"),
            new PermissionDefinition("CREATE_MEETING", "Create Meeting"),
            new PermissionDefinition("EDIT_MEETING", "Edit Meeting"),
            new PermissionDefinition("DELETE_MEETING", "Delete Meeting"),
            new PermissionDefinition("RECORD_MEETING_ATTENDANCE", "Record Attendance"),
            new PermissionDefinition("MANAGE_MEETING_ATTENDANCE", "Manage Meeting Attendance"),
            new PermissionDefinition("MANAGE_MEETING_FINES", "Manage Meeting Fines"),
            new PermissionDefinition("UPLOAD_MEETING_MINUTES", "Upload Meeting Minutes"),
            new PermissionDefinition("VIEW_MEETING_MINUTES", "View Meeting Minutes"),
            new PermissionDefinition("DOWNLOAD_MEETING_MINUTES", "Download Meeting Minutes"),
            new PermissionDefinition("DELETE_MEETING_MINUTES", "Delete Meeting Minutes"),
            new PermissionDefinition("CONFIRM_MEETING_MINUTES", "Confirm Meeting Minutes")
        ));
        
        // Welfare
        permissions.put("Welfare", java.util.List.of(
            new PermissionDefinition("VIEW_WELFARE_EVENTS", "View Welfare Events"),
            new PermissionDefinition("CREATE_WELFARE_EVENT", "Create Welfare Event"),
            new PermissionDefinition("EDIT_WELFARE_EVENT", "Edit Welfare Event"),
            new PermissionDefinition("DELETE_WELFARE_EVENT", "Delete Welfare Event"),
            new PermissionDefinition("APPROVE_WELFARE_CLAIM", "Approve Welfare Claim"),
            new PermissionDefinition("DISBURSE_WELFARE", "Disburse Welfare"),
            new PermissionDefinition("MANAGE_WELFARE_CONTRIBUTIONS", "Manage Welfare Contributions")
        ));
        
        // Finance
        permissions.put("Finance", java.util.List.of(
            new PermissionDefinition("VIEW_FINANCE_SUMMARY", "View Finance Summary"),
            new PermissionDefinition("VIEW_TRANSACTIONS", "View Transactions"),
            new PermissionDefinition("RECORD_TRANSACTION", "Record Transaction"),
            new PermissionDefinition("EDIT_TRANSACTION", "Edit Transaction"),
            new PermissionDefinition("DELETE_TRANSACTION", "Delete Transaction")
        ));
        
        // Shares
        permissions.put("Shares", java.util.List.of(
            new PermissionDefinition("VIEW_SHARES", "View Shares"),
            new PermissionDefinition("VIEW_SHARE_SETTINGS", "View Share Settings"),
            new PermissionDefinition("MANAGE_SHARE_SETTINGS", "Manage Share Settings"),
            new PermissionDefinition("RECORD_SHARE_PURCHASE", "Record Share Purchase"),
            new PermissionDefinition("EDIT_SHARE_ENTRY", "Edit Share Entry"),
            new PermissionDefinition("DELETE_SHARE_ENTRY", "Delete Share Entry")
        ));
        
        // Payouts
        permissions.put("Payouts", java.util.List.of(
            new PermissionDefinition("VIEW_PAYOUTS", "View Payouts & Liquidity"),
            new PermissionDefinition("CREATE_PAYOUT", "Create Payout"),
            new PermissionDefinition("APPROVE_PAYOUT", "Approve Payout"),
            new PermissionDefinition("CANCEL_PAYOUT", "Cancel Payout"),
            new PermissionDefinition("PROCESS_PAYOUT", "Process Payout (Mark Paid)")
        ));
        
        // Investments
        permissions.put("Investments", java.util.List.of(
            new PermissionDefinition("VIEW_INVESTMENTS", "View Investments"),
            new PermissionDefinition("CREATE_INVESTMENT", "Create Investment"),
            new PermissionDefinition("EDIT_INVESTMENT", "Edit Investment"),
            new PermissionDefinition("DELETE_INVESTMENT", "Delete Investment"),
            new PermissionDefinition("CLOSE_INVESTMENT", "Close Investment")
        ));
        
        // Merry-Go-Round
        permissions.put("Merry-Go-Round", java.util.List.of(
            new PermissionDefinition("VIEW_MERRY_GO_ROUND", "View Merry-Go-Round"),
            new PermissionDefinition("START_MERRY_GO_ROUND_CYCLE", "Start New Cycle"),
            new PermissionDefinition("CREATE_MGR_CYCLE", "Create Cycle"),
            new PermissionDefinition("EDIT_MGR_CYCLE", "Edit Cycle"),
            new PermissionDefinition("DELETE_MGR_CYCLE", "Delete Cycle"),
            new PermissionDefinition("COMPLETE_MGR_ROUND", "Complete Round / Mark Payout"),
            new PermissionDefinition("RECORD_MGR_CONTRIBUTION", "Record MGR Contribution"),
            new PermissionDefinition("IMPOSE_MGR_FINE", "Impose MGR Fine"),
            new PermissionDefinition("PAY_MGR_FINE", "Pay MGR Fine"),
            new PermissionDefinition("WAIVE_MGR_FINE", "Waive MGR Fine")
        ));
        
        // Fines
        permissions.put("Fines", java.util.List.of(
            new PermissionDefinition("VIEW_FINES", "View Fines"),
            new PermissionDefinition("RECORD_FINE", "Record Fine"),
            new PermissionDefinition("EDIT_FINE", "Edit Fine"),
            new PermissionDefinition("DELETE_FINE", "Delete Fine"),
            new PermissionDefinition("WAIVE_FINE", "Waive Fine")
        ));
        
        // Penalties
        permissions.put("Penalties", java.util.List.of(
            new PermissionDefinition("VIEW_PENALTIES", "View Penalties"),
            new PermissionDefinition("RECORD_PENALTY", "Record Penalty"),
            new PermissionDefinition("EDIT_PENALTY", "Edit Penalty"),
            new PermissionDefinition("DELETE_PENALTY", "Delete Penalty")
        ));
        
        // Roles
        permissions.put("Roles", java.util.List.of(
            new PermissionDefinition("VIEW_ROLES", "View Roles"),
            new PermissionDefinition("CREATE_ROLE", "Create Role"),
            new PermissionDefinition("EDIT_ROLE", "Edit Role"),
            new PermissionDefinition("DELETE_ROLE", "Delete Role"),
            new PermissionDefinition("MANAGE_ROLE_PERMISSIONS", "Manage Permissions")
        ));
        
        // Settings
        permissions.put("Settings", java.util.List.of(
            new PermissionDefinition("VIEW_TENANT_SETTINGS", "View General Settings"),
            new PermissionDefinition("EDIT_TENANT_SETTINGS", "Edit General Settings"),
            new PermissionDefinition("VIEW_CONTRIBUTION_TYPES", "View Contribution Types"),
            new PermissionDefinition("CREATE_CONTRIBUTION_TYPE", "Create Contribution Type"),
            new PermissionDefinition("EDIT_CONTRIBUTION_TYPE", "Edit Contribution Type"),
            new PermissionDefinition("DELETE_CONTRIBUTION_TYPE", "Delete Contribution Type"),
            new PermissionDefinition("VIEW_LOAN_PRODUCTS", "View Loan Products"),
            new PermissionDefinition("CREATE_LOAN_PRODUCT", "Create Loan Product"),
            new PermissionDefinition("EDIT_LOAN_PRODUCT", "Edit Loan Product"),
            new PermissionDefinition("DELETE_LOAN_PRODUCT", "Delete Loan Product"),
            new PermissionDefinition("VIEW_FINE_TYPES", "View Fine Types"),
            new PermissionDefinition("CREATE_FINE_TYPE", "Create Fine Type"),
            new PermissionDefinition("EDIT_FINE_TYPE", "Edit Fine Type"),
            new PermissionDefinition("DELETE_FINE_TYPE", "Delete Fine Type")
        ));
        
        return permissions;
    }
    
    /**
     * Simple record to hold permission code and display name
     */
    public record PermissionDefinition(String code, String displayName) {}
}
