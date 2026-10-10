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
            .requestMatchers("/api/auth/login", "/api/auth/login/**", "/api/auth/super-admin/login").permitAll()
            .requestMatchers("GET", "/api/platform/contact-info").permitAll()  // Public contact info for login page
            // Change password, logout, and session verification require authentication
            .requestMatchers("/api/auth/change-password").authenticated()
            .requestMatchers("/api/auth/logout").authenticated()
            .requestMatchers("/api/auth/me").authenticated()  // Session validation endpoint
            .requestMatchers("/api/permissions").hasAnyAuthority("FULL_ACCESS", "VIEW_ROLES", "MANAGE_ROLE_PERMISSIONS")  // Get available permissions for roles UI
            .requestMatchers("/h2-console/**").permitAll()
            
            // ==================== MEMBERS ====================
            .requestMatchers("GET", "/api/members", "/api/members/active").hasAnyAuthority("FULL_ACCESS", "VIEW_MEMBERS")
            .requestMatchers("GET", "/api/members/paginated").hasAnyAuthority("FULL_ACCESS", "VIEW_MEMBERS")
            .requestMatchers("GET", "/api/members/{id}").hasAnyAuthority("FULL_ACCESS", "VIEW_MEMBER_DETAILS")
            .requestMatchers("GET", "/api/members/{id}/financial-summary").hasAnyAuthority("FULL_ACCESS", "VIEW_MEMBER_DETAILS")
            .requestMatchers("GET", "/api/members/{id}/transactions/**").hasAnyAuthority("FULL_ACCESS", "VIEW_MEMBER_DETAILS")
            .requestMatchers("POST", "/api/members").hasAnyAuthority("FULL_ACCESS", "CREATE_MEMBER")
            .requestMatchers("PUT", "/api/members/{id}").hasAnyAuthority("FULL_ACCESS", "EDIT_MEMBER")
            .requestMatchers("DELETE", "/api/members/{id}").hasAnyAuthority("FULL_ACCESS", "DELETE_MEMBER")
            .requestMatchers("DELETE", "/api/members/{id}/hard").hasAnyAuthority("FULL_ACCESS", "HARD_DELETE_MEMBER")
            .requestMatchers("PATCH", "/api/members/{id}/deactivate").hasAnyAuthority("FULL_ACCESS", "DEACTIVATE_MEMBER")
            .requestMatchers("PATCH", "/api/members/{id}/activate").hasAnyAuthority("FULL_ACCESS", "ACTIVATE_MEMBER")
            .requestMatchers("POST", "/api/members/reset-password").hasAnyAuthority("FULL_ACCESS", "RESET_PASSWORD")
            
            // ==================== CONTRIBUTIONS ====================
            .requestMatchers("GET", "/api/contributions/reports").hasAnyAuthority("FULL_ACCESS", "VIEW_CONTRIBUTION_REPORTS")
            .requestMatchers("GET", "/api/contributions/reports/summary").hasAnyAuthority("FULL_ACCESS", "VIEW_CONTRIBUTION_REPORTS")
            .requestMatchers("GET", "/api/contributions/reports/pdf").hasAnyAuthority("FULL_ACCESS", "DOWNLOAD_CONTRIBUTION_REPORT")
            .requestMatchers("GET", "/api/contributions/reports/transactions").hasAnyAuthority("FULL_ACCESS", "VIEW_CONTRIBUTION_REPORTS")
            .requestMatchers("GET", "/api/contributions/reports/transactions/summary").hasAnyAuthority("FULL_ACCESS", "VIEW_CONTRIBUTION_REPORTS")
            .requestMatchers("GET", "/api/contributions/reports/transactions/pdf").hasAnyAuthority("FULL_ACCESS", "DOWNLOAD_CONTRIBUTION_REPORT")
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
            // Specific paths MUST come before path variable patterns
            .requestMatchers("GET", "/api/loans/my-loans").authenticated()  // Any authenticated user can see their own loans
            .requestMatchers("GET", "/api/loans/my-credit-score").authenticated()  // Any authenticated user can see their credit score
            .requestMatchers("GET", "/api/loans/pending-approvals").hasAnyAuthority("FULL_ACCESS", "VIEW_PENDING_LOAN_APPROVALS")
            .requestMatchers("POST", "/api/loans/check-eligibility").authenticated()  // Any member can check loan eligibility
            .requestMatchers("POST", "/api/loans/apply").hasAnyAuthority("FULL_ACCESS", "APPLY_FOR_LOAN")
            .requestMatchers("GET", "/api/loans/{id}").hasAnyAuthority("FULL_ACCESS", "VIEW_LOANS")
            .requestMatchers("GET", "/api/loans").hasAnyAuthority("FULL_ACCESS", "VIEW_LOANS")
            .requestMatchers("POST", "/api/loans").hasAnyAuthority("FULL_ACCESS", "APPLY_FOR_LOAN")
            .requestMatchers("PUT", "/api/loans/{id}").hasAnyAuthority("FULL_ACCESS", "EDIT_LOAN")
            .requestMatchers("DELETE", "/api/loans/{id}").hasAnyAuthority("FULL_ACCESS", "DELETE_LOAN")
            .requestMatchers("POST", "/api/loans/{id}/approve").hasAnyAuthority("FULL_ACCESS", "APPROVE_LOAN")
            .requestMatchers("POST", "/api/loans/{id}/reject").hasAnyAuthority("FULL_ACCESS", "REJECT_LOAN")
            .requestMatchers("POST", "/api/loans/{id}/disburse").hasAnyAuthority("FULL_ACCESS", "DISBURSE_LOAN")
            .requestMatchers("POST", "/api/loans/{id}/repay").hasAnyAuthority("FULL_ACCESS", "RECORD_LOAN_REPAYMENT")
            
            // ==================== LOAN PENALTIES (under /api/loans/penalties/) ====================
            .requestMatchers("GET", "/api/loans/penalties/my-penalties").authenticated()
            .requestMatchers("GET", "/api/loans/penalties/**").hasAnyAuthority("FULL_ACCESS", "VIEW_LOAN_PENALTIES")
            .requestMatchers("POST", "/api/loans/penalties/{id}/waive").hasAnyAuthority("FULL_ACCESS", "WAIVE_LOAN_PENALTY")
            .requestMatchers("POST", "/api/loans/penalties/{id}/pay").hasAnyAuthority("FULL_ACCESS", "PAY_LOAN_PENALTY")
            
            // ==================== LOAN PRODUCTS (Settings) ====================
            .requestMatchers("GET", "/api/loan-products/**").authenticated()  // Any member can view loan products
            .requestMatchers("POST", "/api/loan-products").hasAnyAuthority("FULL_ACCESS", "CREATE_LOAN_PRODUCT")
            .requestMatchers("PUT", "/api/loan-products/{id}").hasAnyAuthority("FULL_ACCESS", "EDIT_LOAN_PRODUCT")
            .requestMatchers("DELETE", "/api/loan-products/{id}").hasAnyAuthority("FULL_ACCESS", "DELETE_LOAN_PRODUCT")
            
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
            .requestMatchers("POST", "/api/welfare/{id}/cancel").hasAnyAuthority("FULL_ACCESS", "CANCEL_WELFARE")
            .requestMatchers("PUT", "/api/welfare/{id}/contributions/**").hasAnyAuthority("FULL_ACCESS", "MANAGE_WELFARE_CONTRIBUTIONS")
            .requestMatchers("POST", "/api/welfare/{id}/contributions/**").hasAnyAuthority("FULL_ACCESS", "MANAGE_WELFARE_CONTRIBUTIONS")
            
            // ==================== FINANCE ====================
            .requestMatchers("GET", "/api/finance/report/pdf").hasAnyAuthority("FULL_ACCESS", "DOWNLOAD_FINANCE_REPORT")
            .requestMatchers("GET", "/api/finance/**").hasAnyAuthority("FULL_ACCESS", "VIEW_FINANCE_SUMMARY")
            .requestMatchers("GET", "/api/finance/transactions/**").hasAnyAuthority("FULL_ACCESS", "VIEW_TRANSACTIONS")
            .requestMatchers("POST", "/api/finance/transactions").hasAnyAuthority("FULL_ACCESS", "RECORD_TRANSACTION")
            .requestMatchers("PUT", "/api/finance/transactions/{id}").hasAnyAuthority("FULL_ACCESS", "EDIT_TRANSACTION")
            .requestMatchers("DELETE", "/api/finance/transactions/{id}").hasAnyAuthority("FULL_ACCESS", "DELETE_TRANSACTION")
            
            // ==================== SHARES ====================
            .requestMatchers("GET", "/api/shares/members/paginated").hasAnyAuthority("FULL_ACCESS", "VIEW_SHARES")
            .requestMatchers("GET", "/api/shares/settings").hasAnyAuthority("FULL_ACCESS", "VIEW_SHARE_SETTINGS")
            .requestMatchers("PUT", "/api/shares/settings").hasAnyAuthority("FULL_ACCESS", "MANAGE_SHARE_SETTINGS")
            .requestMatchers("GET", "/api/shares/**").hasAnyAuthority("FULL_ACCESS", "VIEW_SHARES")
            .requestMatchers("POST", "/api/shares").hasAnyAuthority("FULL_ACCESS", "RECORD_SHARE_PURCHASE")
            .requestMatchers("PUT", "/api/shares/{id}").hasAnyAuthority("FULL_ACCESS", "EDIT_SHARE_ENTRY")
            .requestMatchers("DELETE", "/api/shares/{id}").hasAnyAuthority("FULL_ACCESS", "DELETE_SHARE_ENTRY")
            
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
            .requestMatchers("GET", "/api/investments").hasAnyAuthority("FULL_ACCESS", "VIEW_INVESTMENTS")
            .requestMatchers("GET", "/api/investments/paginated").hasAnyAuthority("FULL_ACCESS", "VIEW_INVESTMENTS")
            .requestMatchers("GET", "/api/investments/my-pending-approvals").hasAnyAuthority("FULL_ACCESS", "VIEW_INVESTMENTS", "APPROVE_INVESTMENT")
            .requestMatchers("GET", "/api/investments/my-pending-approvals/count").hasAnyAuthority("FULL_ACCESS", "VIEW_INVESTMENTS", "APPROVE_INVESTMENT")
            .requestMatchers("GET", "/api/investments/{id}").hasAnyAuthority("FULL_ACCESS", "VIEW_INVESTMENTS")
            .requestMatchers("GET", "/api/investments/{id}/history").hasAnyAuthority("FULL_ACCESS", "VIEW_INVESTMENTS")
            .requestMatchers("GET", "/api/investments/{id}/topups").hasAnyAuthority("FULL_ACCESS", "VIEW_INVESTMENTS")
            .requestMatchers("GET", "/api/investments/{id}/approvals").hasAnyAuthority("FULL_ACCESS", "VIEW_INVESTMENTS")
            .requestMatchers("POST", "/api/investments").hasAnyAuthority("FULL_ACCESS", "CREATE_INVESTMENT")
            .requestMatchers("PUT", "/api/investments/{id}").hasAnyAuthority("FULL_ACCESS", "EDIT_INVESTMENT")
            .requestMatchers("DELETE", "/api/investments/{id}").hasAnyAuthority("FULL_ACCESS", "DELETE_INVESTMENT")
            .requestMatchers("POST", "/api/investments/{id}/topup").hasAnyAuthority("FULL_ACCESS", "TOPUP_INVESTMENT")
            .requestMatchers("POST", "/api/investments/{id}/value").hasAnyAuthority("FULL_ACCESS", "UPDATE_INVESTMENT_VALUE")
            .requestMatchers("POST", "/api/investments/{id}/sell").hasAnyAuthority("FULL_ACCESS", "SELL_INVESTMENT")
            .requestMatchers("POST", "/api/investments/{id}/write-off").hasAnyAuthority("FULL_ACCESS", "WRITE_OFF_INVESTMENT")
            .requestMatchers("POST", "/api/investments/{id}/vote").hasAnyAuthority("FULL_ACCESS", "APPROVE_INVESTMENT")
            .requestMatchers("POST", "/api/investments/{id}/approve").hasAnyAuthority("FULL_ACCESS", "APPROVE_INVESTMENT")
            .requestMatchers("POST", "/api/investments/{id}/reject").hasAnyAuthority("FULL_ACCESS", "APPROVE_INVESTMENT")
            
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
            .requestMatchers("POST", "/api/merry-go-round/rounds/{id}/payout").hasAnyAuthority("FULL_ACCESS", "COMPLETE_MGR_ROUND")
            // Contributions
            .requestMatchers("POST", "/api/merry-go-round/contributions/{id}/pay").hasAnyAuthority("FULL_ACCESS", "RECORD_MGR_CONTRIBUTION")
            .requestMatchers("POST", "/api/merry-go-round/contributions/pay-multiple").hasAnyAuthority("FULL_ACCESS", "RECORD_MGR_CONTRIBUTION")
            .requestMatchers("POST", "/api/merry-go-round/contributions/{contributionId}/fine").hasAnyAuthority("FULL_ACCESS", "IMPOSE_MGR_FINE")
            // Fines
            .requestMatchers("GET", "/api/merry-go-round/cycles/{cycleId}/fines").hasAnyAuthority("FULL_ACCESS", "VIEW_ALL_MGR_FINES")
            .requestMatchers("GET", "/api/merry-go-round/cycles/{cycleId}/my-fines").hasAnyAuthority("FULL_ACCESS", "VIEW_MY_MGR_FINES", "VIEW_ALL_MGR_FINES")
            .requestMatchers("POST", "/api/merry-go-round/fines").hasAnyAuthority("FULL_ACCESS", "IMPOSE_MGR_FINE")
            .requestMatchers("POST", "/api/merry-go-round/fines/{id}/pay").hasAnyAuthority("FULL_ACCESS", "PAY_MGR_FINE")
            .requestMatchers("POST", "/api/merry-go-round/fines/{id}/initiate-payment").hasAnyAuthority("FULL_ACCESS", "PAY_MGR_FINE", "VIEW_MY_MGR_FINES")
            .requestMatchers("POST", "/api/merry-go-round/fines/{id}/record-payment").hasAnyAuthority("FULL_ACCESS", "RECORD_MGR_FINE_PAYMENT")
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
            
            // ==================== BILLING (Chama Admin - view own invoices) ====================
            .requestMatchers("GET", "/api/licenses/invoices/my").hasAnyAuthority("FULL_ACCESS", "VIEW_BILLING")
            .requestMatchers("GET", "/api/licenses/invoices/my/{id}").hasAnyAuthority("FULL_ACCESS", "VIEW_BILLING")
            .requestMatchers("GET", "/api/licenses/invoices/my/{id}/pdf").hasAnyAuthority("FULL_ACCESS", "VIEW_BILLING")
            .requestMatchers("GET", "/api/licenses/transactions/my").hasAnyAuthority("FULL_ACCESS", "VIEW_BILLING")
            .requestMatchers("GET", "/api/licenses/my-license").hasAnyAuthority("FULL_ACCESS", "VIEW_BILLING")
            
            // ==================== LICENSE MANAGEMENT (Super Admin only) ====================
            .requestMatchers("GET", "/api/licenses").hasAuthority("FULL_ACCESS")
            .requestMatchers("GET", "/api/licenses/tenant/{tenantId}").hasAuthority("FULL_ACCESS")
            .requestMatchers("POST", "/api/licenses/tenant/{tenantId}").hasAuthority("FULL_ACCESS")
            .requestMatchers("PUT", "/api/licenses/{id}").hasAuthority("FULL_ACCESS")
            .requestMatchers("POST", "/api/licenses/{id}/suspend").hasAuthority("FULL_ACCESS")
            .requestMatchers("POST", "/api/licenses/{id}/activate").hasAuthority("FULL_ACCESS")
            .requestMatchers("GET", "/api/licenses/transactions/tenant/{tenantId}").hasAuthority("FULL_ACCESS")
            .requestMatchers("GET", "/api/licenses/transactions/tenant/{tenantId}/uninvoiced").hasAuthority("FULL_ACCESS")
            .requestMatchers("GET", "/api/licenses/invoices/outstanding").hasAuthority("FULL_ACCESS")
            .requestMatchers("GET", "/api/licenses/invoices/tenant/{tenantId}").hasAuthority("FULL_ACCESS")
            .requestMatchers("GET", "/api/licenses/invoices/{id}").hasAuthority("FULL_ACCESS")
            .requestMatchers("GET", "/api/licenses/invoices/{id}/pdf").hasAuthority("FULL_ACCESS")
            .requestMatchers("POST", "/api/licenses/invoices/generate").hasAuthority("FULL_ACCESS")
            .requestMatchers("POST", "/api/licenses/invoices/{id}/send").hasAuthority("FULL_ACCESS")
            .requestMatchers("POST", "/api/licenses/invoices/{id}/payment").hasAuthority("FULL_ACCESS")
            .requestMatchers("POST", "/api/licenses/invoices/{id}/mark-paid").hasAuthority("FULL_ACCESS")
            .requestMatchers("POST", "/api/licenses/invoices/{id}/cancel").hasAuthority("FULL_ACCESS")
            .requestMatchers("POST", "/api/licenses/check-overdue").hasAuthority("FULL_ACCESS")
            
            // ==================== TENANTS (Super Admin only for most operations) ====================
            // Regular users can access their own tenant settings
            .requestMatchers("GET", "/api/tenants/settings").hasAnyAuthority("FULL_ACCESS", "VIEW_TENANT_SETTINGS")
            .requestMatchers("PUT", "/api/tenants/settings").hasAnyAuthority("FULL_ACCESS", "EDIT_TENANT_SETTINGS")
            // Allow admins with MANAGE_ROLE_PERMISSIONS to change member roles
            .requestMatchers("PUT", "/api/tenants/{tenantId}/members/{memberId}/role").hasAnyAuthority("FULL_ACCESS", "MANAGE_ROLE_PERMISSIONS")
            // Super admin operations for specific tenants
            .requestMatchers("/api/tenants/**").hasAuthority("FULL_ACCESS")
            
            // ==================== SYSTEM SETTINGS (Super Admin only) ====================
            .requestMatchers("/api/system-settings/**").hasAuthority("FULL_ACCESS")
            
            // ==================== PLATFORM SETTINGS (Super Admin only for update) ====================
            .requestMatchers("PUT", "/api/platform/contact-info").hasAuthority("FULL_ACCESS")
            
            // ==================== DASHBOARD ====================
            .requestMatchers("GET", "/api/dashboard/**").hasAnyAuthority("FULL_ACCESS", "VIEW_DASHBOARD")
            
            // ==================== REGISTRATION FEE ====================
            .requestMatchers("GET", "/api/registration-fee/info").authenticated()
            .requestMatchers("GET", "/api/registration-fee/member/{memberId}").authenticated()
            .requestMatchers("GET", "/api/registration-fee").hasAnyAuthority("FULL_ACCESS", "VIEW_REGISTRATION_FEES")
            .requestMatchers("GET", "/api/registration-fee/awaiting-verification").hasAnyAuthority("FULL_ACCESS", "VERIFY_PAYMENTS")
            .requestMatchers("GET", "/api/registration-fee/awaiting-verification/count").hasAnyAuthority("FULL_ACCESS", "VERIFY_PAYMENTS")
            .requestMatchers("POST", "/api/registration-fee/pay").hasAnyAuthority("FULL_ACCESS", "PAY_REGISTRATION_FEE", "PAY_ALL_REGISTRATION_FEE")
            .requestMatchers("POST", "/api/registration-fee/{id}/verify").hasAnyAuthority("FULL_ACCESS", "VERIFY_PAYMENTS")
            .requestMatchers("POST", "/api/registration-fee/{id}/reject").hasAnyAuthority("FULL_ACCESS", "VERIFY_PAYMENTS")
            
            // ==================== RESOURCES ====================
            .requestMatchers("GET", "/api/resources").hasAnyAuthority("FULL_ACCESS", "VIEW_RESOURCES")
            .requestMatchers("GET", "/api/resources/paginated").hasAnyAuthority("FULL_ACCESS", "VIEW_RESOURCES")
            .requestMatchers("GET", "/api/resources/{id}").hasAnyAuthority("FULL_ACCESS", "VIEW_RESOURCES")
            .requestMatchers("GET", "/api/resources/{id}/download").hasAnyAuthority("FULL_ACCESS", "DOWNLOAD_RESOURCE")
            .requestMatchers("POST", "/api/resources").hasAnyAuthority("FULL_ACCESS", "UPLOAD_RESOURCE")
            .requestMatchers("PUT", "/api/resources/{id}").hasAnyAuthority("FULL_ACCESS", "EDIT_RESOURCE")
            .requestMatchers("DELETE", "/api/resources/{id}").hasAnyAuthority("FULL_ACCESS", "DELETE_RESOURCE")
            .requestMatchers("DELETE", "/api/resources/{id}/hard").hasAnyAuthority("FULL_ACCESS", "DELETE_RESOURCE")
            
            // ==================== AUDIT LOGS ====================
            .requestMatchers("GET", "/api/audit-logs").hasAnyAuthority("FULL_ACCESS", "VIEW_AUDIT_LOGS")
            .requestMatchers("GET", "/api/audit-logs/{id}").hasAnyAuthority("FULL_ACCESS", "VIEW_AUDIT_DETAILS")
            .requestMatchers("GET", "/api/audit-logs/modules").hasAnyAuthority("FULL_ACCESS", "VIEW_AUDIT_LOGS")
            .requestMatchers("GET", "/api/audit-logs/actions").hasAnyAuthority("FULL_ACCESS", "VIEW_AUDIT_LOGS")
            .requestMatchers("GET", "/api/audit-logs/statistics").hasAnyAuthority("FULL_ACCESS", "VIEW_AUDIT_STATISTICS")
            .requestMatchers("GET", "/api/audit-logs/all").hasAuthority("FULL_ACCESS") // Super admin only
            .requestMatchers("DELETE", "/api/audit-logs/cleanup").hasAnyAuthority("FULL_ACCESS", "CLEANUP_AUDIT_LOGS")
            
            // ==================== CONTRIBUTION TOP-UPS ====================
            .requestMatchers("GET", "/api/contribution-topups").hasAnyAuthority("FULL_ACCESS", "VIEW_CONTRIBUTION_TOPUPS")
            .requestMatchers("GET", "/api/contribution-topups/my").hasAnyAuthority("FULL_ACCESS", "REQUEST_CONTRIBUTION_TOPUP")
            .requestMatchers("GET", "/api/contribution-topups/{id}").hasAnyAuthority("FULL_ACCESS", "VIEW_CONTRIBUTION_TOPUPS")
            .requestMatchers("GET", "/api/contribution-topups/pending-count").hasAnyAuthority("FULL_ACCESS", "VIEW_CONTRIBUTION_TOPUPS")
            .requestMatchers("POST", "/api/contribution-topups").hasAnyAuthority("FULL_ACCESS", "REQUEST_CONTRIBUTION_TOPUP")
            .requestMatchers("POST", "/api/contribution-topups/{id}/approve").hasAnyAuthority("FULL_ACCESS", "APPROVE_CONTRIBUTION_TOPUP")
            .requestMatchers("POST", "/api/contribution-topups/{id}/reject").hasAnyAuthority("FULL_ACCESS", "APPROVE_CONTRIBUTION_TOPUP")
            
            // ==================== PAYMENT SETTINGS ====================
            .requestMatchers("GET", "/api/payment-settings").hasAnyAuthority("FULL_ACCESS", "VIEW_PAYMENT_SETTINGS")
            .requestMatchers("PUT", "/api/payment-settings").hasAnyAuthority("FULL_ACCESS", "MANAGE_PAYMENT_SETTINGS")
            .requestMatchers("GET", "/api/payment-settings/methods").authenticated()  // All users can see enabled methods
            .requestMatchers("GET", "/api/payment-settings/stk-push/available").authenticated()
            .requestMatchers("GET", "/api/payment-settings/in-app-sms/available").authenticated()
            .requestMatchers("PATCH", "/api/payment-settings/methods/{method}/toggle").hasAnyAuthority("FULL_ACCESS", "MANAGE_PAYMENT_SETTINGS")
            
            // ==================== PAYMENTS (Universal Payment System) ====================
            .requestMatchers("POST", "/api/payments/claims").authenticated()  // Any member can create a payment claim
            .requestMatchers("GET", "/api/payments/claims/{id}").authenticated()
            .requestMatchers("GET", "/api/payments/claims/{id}/status").authenticated()
            .requestMatchers("PATCH", "/api/payments/claims/{id}/reference").authenticated()
            .requestMatchers("POST", "/api/payments/claims/{id}/verify").hasAnyAuthority("FULL_ACCESS", "VERIFY_PAYMENTS")
            .requestMatchers("POST", "/api/payments/claims/{id}/reject").hasAnyAuthority("FULL_ACCESS", "VERIFY_PAYMENTS")
            .requestMatchers("GET", "/api/payments/pending-review").hasAnyAuthority("FULL_ACCESS", "VERIFY_PAYMENTS")
            .requestMatchers("GET", "/api/payments/my-claims").authenticated()
            // Payment Events (Treasurer device SMS upload)
            .requestMatchers("POST", "/api/payments/events").hasAnyAuthority("FULL_ACCESS", "UPLOAD_PAYMENT_EVENTS")
            .requestMatchers("GET", "/api/payments/events/unmatched").hasAnyAuthority("FULL_ACCESS", "VERIFY_PAYMENTS")
            .requestMatchers("POST", "/api/payments/events/{id}/manual-match").hasAnyAuthority("FULL_ACCESS", "VERIFY_PAYMENTS")
            // STK Push
            .requestMatchers("POST", "/api/payments/stk-push").authenticated()
            .requestMatchers("POST", "/api/payments/stk-push/callback").permitAll()  // Safaricom callback
            // Treasurer Devices
            .requestMatchers("POST", "/api/payments/devices/register").hasAnyAuthority("FULL_ACCESS", "REGISTER_TREASURER_DEVICE")
            .requestMatchers("GET", "/api/payments/devices").hasAnyAuthority("FULL_ACCESS", "VIEW_TREASURER_DEVICES")
            .requestMatchers("POST", "/api/payments/devices/{id}/revoke").hasAnyAuthority("FULL_ACCESS", "MANAGE_TREASURER_DEVICES")
            // Payables
            .requestMatchers("GET", "/api/payables/my").authenticated()  // Members see their own payables
            .requestMatchers("GET", "/api/payables/{id}").authenticated()
            .requestMatchers("GET", "/api/payables").hasAnyAuthority("FULL_ACCESS", "VIEW_ALL_PAYABLES")
            // Payment Dashboard (Treasurer view)
            .requestMatchers("GET", "/api/payments/dashboard").hasAnyAuthority("FULL_ACCESS", "VIEW_PAYMENT_DASHBOARD")
            // Unified Payment Confirmation/Rejection (Admin action)
            .requestMatchers("POST", "/api/payments/payables/*/confirm").hasAnyAuthority("FULL_ACCESS", "VERIFY_PAYMENTS")
            .requestMatchers("POST", "/api/payments/payables/*/reject").hasAnyAuthority("FULL_ACCESS", "VERIFY_PAYMENTS")
            
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
            new PermissionDefinition("DELETE_MEMBER", "Delete Member (Soft)"),
            new PermissionDefinition("HARD_DELETE_MEMBER", "Permanently Delete Member & Data"),
            new PermissionDefinition("ACTIVATE_MEMBER", "Activate Member"),
            new PermissionDefinition("DEACTIVATE_MEMBER", "Deactivate Member"),
            new PermissionDefinition("RESET_PASSWORD", "Reset Member Password")
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
            new PermissionDefinition("WAIVE_PENALTY", "Waive Penalty"),
            new PermissionDefinition("VIEW_CONTRIBUTION_TOPUPS", "View Top-Up Requests"),
            new PermissionDefinition("REQUEST_CONTRIBUTION_TOPUP", "Request Contribution Top-Up"),
            new PermissionDefinition("APPROVE_CONTRIBUTION_TOPUP", "Approve/Reject Top-Up Requests"),
            new PermissionDefinition("VIEW_CONTRIBUTION_REPORTS", "View Contribution Reports"),
            new PermissionDefinition("DOWNLOAD_CONTRIBUTION_REPORT", "Download Contribution Report PDF")
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
            new PermissionDefinition("CANCEL_WELFARE", "Cancel Welfare Event"),
            new PermissionDefinition("MANAGE_WELFARE_CONTRIBUTIONS", "Manage Welfare Contributions"),
            new PermissionDefinition("PAY_WELFARE_FROM_CONTRIBUTION", "Pay Welfare from Contribution Fund")
        ));
        
        // Finance
        permissions.put("Finance", java.util.List.of(
            new PermissionDefinition("VIEW_FINANCE_SUMMARY", "View Finance Summary"),
            new PermissionDefinition("VIEW_TRANSACTIONS", "View Transactions"),
            new PermissionDefinition("DOWNLOAD_FINANCE_REPORT", "Download Finance Report"),
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
            new PermissionDefinition("CREATE_INVESTMENT", "Create/Propose Investment"),
            new PermissionDefinition("EDIT_INVESTMENT", "Edit Investment"),
            new PermissionDefinition("DELETE_INVESTMENT", "Delete Investment"),
            new PermissionDefinition("TOPUP_INVESTMENT", "Add Principal (Top-up)"),
            new PermissionDefinition("UPDATE_INVESTMENT_VALUE", "Update Investment Value"),
            new PermissionDefinition("SELL_INVESTMENT", "Sell Investment"),
            new PermissionDefinition("WRITE_OFF_INVESTMENT", "Write Off Investment"),
            new PermissionDefinition("APPROVE_INVESTMENT", "Approve/Reject Investment")
        ));
        
        // Merry-Go-Round
        permissions.put("Merry-Go-Round", java.util.List.of(
            new PermissionDefinition("VIEW_MERRY_GO_ROUND", "View Merry-Go-Round"),
            new PermissionDefinition("VIEW_MY_MGR_FINES", "View My MGR Fines"),
            new PermissionDefinition("VIEW_ALL_MGR_FINES", "View All MGR Fines"),
            new PermissionDefinition("START_MERRY_GO_ROUND_CYCLE", "Start New Cycle"),
            new PermissionDefinition("CREATE_MGR_CYCLE", "Create Cycle"),
            new PermissionDefinition("EDIT_MGR_CYCLE", "Edit Cycle"),
            new PermissionDefinition("DELETE_MGR_CYCLE", "Delete Cycle"),
            new PermissionDefinition("COMPLETE_MGR_ROUND", "Complete Round / Mark Payout"),
            new PermissionDefinition("RECORD_MGR_CONTRIBUTION", "Record MGR Contribution"),
            new PermissionDefinition("IMPOSE_MGR_FINE", "Impose MGR Fine"),
            new PermissionDefinition("PAY_MGR_FINE", "Pay MGR Fine"),
            new PermissionDefinition("RECORD_MGR_FINE_PAYMENT", "Record MGR Fine Payment (Admin)"),
            new PermissionDefinition("CONFIRM_MGR_FINE_PAYMENT", "Confirm MGR Fine Payment"),
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
        
        // Billing
        permissions.put("Billing", java.util.List.of(
            new PermissionDefinition("VIEW_BILLING", "View Billing & Invoices")
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
        
        // Registration Fee
        permissions.put("Registration Fee", java.util.List.of(
            new PermissionDefinition("VIEW_REGISTRATION_FEES", "View Registration Fee Payments"),
            new PermissionDefinition("PAY_REGISTRATION_FEE", "Pay Own Registration Fee"),
            new PermissionDefinition("PAY_ALL_REGISTRATION_FEE", "Pay Registration Fee for Any Member")
        ));
        
        // Payment Settings
        permissions.put("Payment Settings", java.util.List.of(
            new PermissionDefinition("VIEW_PAYMENT_SETTINGS", "View Payment Settings"),
            new PermissionDefinition("MANAGE_PAYMENT_SETTINGS", "Manage Payment Settings"),
            new PermissionDefinition("VERIFY_PAYMENTS", "Verify/Reject Payments"),
            new PermissionDefinition("VIEW_PAYMENT_DASHBOARD", "View Payment Dashboard"),
            new PermissionDefinition("UPLOAD_PAYMENT_EVENTS", "Upload Payment Events (Treasurer)"),
            new PermissionDefinition("REGISTER_TREASURER_DEVICE", "Register Treasurer Device"),
            new PermissionDefinition("VIEW_TREASURER_DEVICES", "View Treasurer Devices"),
            new PermissionDefinition("MANAGE_TREASURER_DEVICES", "Manage Treasurer Devices"),
            new PermissionDefinition("VIEW_ALL_PAYABLES", "View All Payables")
        ));
        
        // Resources
        permissions.put("Resources", java.util.List.of(
            new PermissionDefinition("VIEW_RESOURCES", "View Resources"),
            new PermissionDefinition("DOWNLOAD_RESOURCE", "Download Resources"),
            new PermissionDefinition("UPLOAD_RESOURCE", "Upload Resources"),
            new PermissionDefinition("EDIT_RESOURCE", "Edit Resource Details"),
            new PermissionDefinition("DELETE_RESOURCE", "Delete Resources")
        ));
        
        // Audit Trail
        permissions.put("Audit Trail", java.util.List.of(
            new PermissionDefinition("VIEW_AUDIT_LOGS", "View Audit Logs"),
            new PermissionDefinition("VIEW_AUDIT_DETAILS", "View Audit Log Details"),
            new PermissionDefinition("VIEW_AUDIT_STATISTICS", "View Audit Statistics"),
            new PermissionDefinition("CLEANUP_AUDIT_LOGS", "Clean Up Old Audit Logs")
        ));
        
        return permissions;
    }
    
    /**
     * Simple record to hold permission code and display name
     */
    public record PermissionDefinition(String code, String displayName) {}
}
