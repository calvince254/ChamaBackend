package com.calpex_technologies.chama.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Handles database schema migrations that Hibernate's ddl-auto can't do.
 * For example, converting ENUM columns to VARCHAR.
 * Runs BEFORE DataInitializer (Order 1 vs Order 2).
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(1)
public class DatabaseMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        convertFinesStatusToVarchar();
        expandInvestmentCategoryColumn();
        convertTransactionsTypeToVarchar();
        setDefaultMustChangePassword();
        migratePenaltyTypes();
        convertLoansStatusToVarchar();
        addLoanGuarantorIndexColumn();
        fixLoanApprovalLevelColumn();
        addFirstLoginColumn();
        createPasswordHistoryTable();
        fixAuditLogMemberForeignKey();
        addContributionTypeCategoryColumn();
        expandWelfareContributionPaymentMethodColumn();
        addContributionTypeToTransactions();
        backfillContributionTypeOnTransactions();
        addSourceContributionTypeToWelfareContributions();
    }

    /**
     * Convert fines.status from MySQL ENUM to VARCHAR(20).
     * This allows adding new status values without manual migration.
     */
    private void convertFinesStatusToVarchar() {
        try {
            // Check if the fines table exists
            String checkTable = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'fines'";
            Integer tableExists = jdbcTemplate.queryForObject(checkTable, Integer.class);
            
            if (tableExists == null || tableExists == 0) {
                log.info("Fines table doesn't exist yet, skipping migration");
                return;
            }

            // Check current column type
            String checkType = "SELECT DATA_TYPE FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'fines' AND column_name = 'status'";
            String dataType = jdbcTemplate.queryForObject(checkType, String.class);

            if ("enum".equalsIgnoreCase(dataType)) {
                log.info("Converting fines.status from ENUM to VARCHAR(20)...");
                jdbcTemplate.execute("ALTER TABLE fines MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'UNPAID'");
                log.info("Successfully converted fines.status to VARCHAR(20)");
            } else {
                log.debug("fines.status is already VARCHAR, no migration needed");
            }
        } catch (Exception e) {
            log.warn("Could not migrate fines.status column: {}. This may be expected on first run.", e.getMessage());
        }
    }

    /**
     * Expand investments.category column to VARCHAR(50) to accommodate longer category names like REAL_ESTATE.
     */
    private void expandInvestmentCategoryColumn() {
        try {
            // Check if the investments table exists
            String checkTable = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'investments'";
            Integer tableExists = jdbcTemplate.queryForObject(checkTable, Integer.class);
            
            if (tableExists == null || tableExists == 0) {
                log.info("Investments table doesn't exist yet, skipping migration");
                return;
            }

            // Check current column length
            String checkLength = "SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'investments' AND column_name = 'category'";
            Integer currentLength = jdbcTemplate.queryForObject(checkLength, Integer.class);

            if (currentLength != null && currentLength < 50) {
                log.info("Expanding investments.category from VARCHAR({}) to VARCHAR(50)...", currentLength);
                jdbcTemplate.execute("ALTER TABLE investments MODIFY COLUMN category VARCHAR(50) NOT NULL");
                log.info("Successfully expanded investments.category to VARCHAR(50)");
            } else {
                log.debug("investments.category is already large enough, no migration needed");
            }
        } catch (Exception e) {
            log.warn("Could not migrate investments.category column: {}. This may be expected on first run.", e.getMessage());
        }
    }

    /**
     * Convert transactions.type from MySQL ENUM to VARCHAR(30).
     * This allows adding new transaction types like PAYOUT without manual migration.
     */
    private void convertTransactionsTypeToVarchar() {
        try {
            // Check if the transactions table exists
            String checkTable = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'transactions'";
            Integer tableExists = jdbcTemplate.queryForObject(checkTable, Integer.class);
            
            if (tableExists == null || tableExists == 0) {
                log.info("Transactions table doesn't exist yet, skipping migration");
                return;
            }

            // Check current column type
            String checkType = "SELECT DATA_TYPE FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'transactions' AND column_name = 'type'";
            String dataType = jdbcTemplate.queryForObject(checkType, String.class);

            if ("enum".equalsIgnoreCase(dataType)) {
                log.info("Converting transactions.type from ENUM to VARCHAR(30)...");
                jdbcTemplate.execute("ALTER TABLE transactions MODIFY COLUMN type VARCHAR(30) NOT NULL");
                log.info("Successfully converted transactions.type to VARCHAR(30)");
            } else {
                log.debug("transactions.type is already VARCHAR, no migration needed");
            }
        } catch (Exception e) {
            log.warn("Could not migrate transactions.type column: {}. This may be expected on first run.", e.getMessage());
        }
    }

    /**
     * Set default value for must_change_password column for existing members.
     * This is needed because the column was added as nullable and existing records have NULL.
     */
    private void setDefaultMustChangePassword() {
        try {
            // Check if the members table exists
            String checkTable = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'members'";
            Integer tableExists = jdbcTemplate.queryForObject(checkTable, Integer.class);
            
            if (tableExists == null || tableExists == 0) {
                log.info("Members table doesn't exist yet, skipping migration");
                return;
            }

            // Check if the column exists
            String checkColumn = "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'members' AND column_name = 'must_change_password'";
            Integer columnExists = jdbcTemplate.queryForObject(checkColumn, Integer.class);
            
            if (columnExists == null || columnExists == 0) {
                log.info("must_change_password column doesn't exist yet, skipping migration");
                return;
            }

            // Update NULL values to false
            log.info("Setting default value for must_change_password on existing members...");
            int updated = jdbcTemplate.update("UPDATE members SET must_change_password = 0 WHERE must_change_password IS NULL");
            if (updated > 0) {
                log.info("Updated {} members with default must_change_password = false", updated);
            } else {
                log.debug("No members needed must_change_password update");
            }
        } catch (Exception e) {
            log.warn("Could not update must_change_password column: {}. This may be expected on first run.", e.getMessage());
        }
    }

    /**
     * Migrate penalty types:
     * 1. Convert penalty_type from ENUM to VARCHAR first (preserves existing data)
     * 2. Rename LATE_PAYMENT to CONTRIBUTION_LATE_PAYMENT for contribution-related penalties
     * 3. Rename LATE_PAYMENT to MEETING_LATE_ARRIVAL for meeting-related penalties
     */
    private void migratePenaltyTypes() {
        try {
            // Check if the penalties table exists
            String checkTable = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'penalties'";
            Integer tableExists = jdbcTemplate.queryForObject(checkTable, Integer.class);
            
            if (tableExists == null || tableExists == 0) {
                log.info("Penalties table doesn't exist yet, skipping migration");
                return;
            }

            // Check current column type
            String checkType = "SELECT DATA_TYPE FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'penalties' AND column_name = 'penalty_type'";
            String dataType = jdbcTemplate.queryForObject(checkType, String.class);

            if ("enum".equalsIgnoreCase(dataType)) {
                log.info("Converting penalties.penalty_type from ENUM to VARCHAR(50)...");
                jdbcTemplate.execute("ALTER TABLE penalties MODIFY COLUMN penalty_type VARCHAR(50) NOT NULL");
                log.info("Successfully converted penalties.penalty_type to VARCHAR(50)");
            } else {
                // Check current column length for VARCHAR
                String checkLength = "SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'penalties' AND column_name = 'penalty_type'";
                Integer currentLength = jdbcTemplate.queryForObject(checkLength, Integer.class);

                if (currentLength != null && currentLength < 50) {
                    log.info("Expanding penalties.penalty_type from VARCHAR({}) to VARCHAR(50)...", currentLength);
                    jdbcTemplate.execute("ALTER TABLE penalties MODIFY COLUMN penalty_type VARCHAR(50) NOT NULL");
                    log.info("Successfully expanded penalties.penalty_type to VARCHAR(50)");
                }
            }

            // Migrate LATE_PAYMENT to CONTRIBUTION_LATE_PAYMENT (only for contribution-related penalties)
            log.info("Migrating penalty types...");
            int updated = jdbcTemplate.update(
                "UPDATE penalties SET penalty_type = 'CONTRIBUTION_LATE_PAYMENT' WHERE penalty_type = 'LATE_PAYMENT' AND contribution_type_id IS NOT NULL");
            if (updated > 0) {
                log.info("Migrated {} penalties from LATE_PAYMENT to CONTRIBUTION_LATE_PAYMENT", updated);
            }

            // Migrate meeting-related LATE_PAYMENT to MEETING_LATE_ARRIVAL
            updated = jdbcTemplate.update(
                "UPDATE penalties SET penalty_type = 'MEETING_LATE_ARRIVAL' WHERE penalty_type = 'LATE_PAYMENT' AND meeting_id IS NOT NULL");
            if (updated > 0) {
                log.info("Migrated {} penalties from LATE_PAYMENT to MEETING_LATE_ARRIVAL", updated);
            }
            
            // Migrate any remaining LATE_PAYMENT (fallback to CONTRIBUTION_LATE_PAYMENT)
            updated = jdbcTemplate.update(
                "UPDATE penalties SET penalty_type = 'CONTRIBUTION_LATE_PAYMENT' WHERE penalty_type = 'LATE_PAYMENT'");
            if (updated > 0) {
                log.info("Migrated {} remaining penalties from LATE_PAYMENT to CONTRIBUTION_LATE_PAYMENT", updated);
            }
        } catch (Exception e) {
            log.warn("Could not migrate penalties.penalty_type column: {}. This may be expected on first run.", e.getMessage());
        }
    }

    /**
     * Convert loans.status from MySQL ENUM to VARCHAR(30).
     * This allows adding new status values without manual migration.
     */
    private void convertLoansStatusToVarchar() {
        try {
            // Check if the loans table exists
            String checkTable = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'loans'";
            Integer tableExists = jdbcTemplate.queryForObject(checkTable, Integer.class);
            
            if (tableExists == null || tableExists == 0) {
                log.info("Loans table doesn't exist yet, skipping migration");
                return;
            }

            // Check current column type
            String checkType = "SELECT DATA_TYPE FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'loans' AND column_name = 'status'";
            String dataType = jdbcTemplate.queryForObject(checkType, String.class);

            if ("enum".equalsIgnoreCase(dataType)) {
                log.info("Converting loans.status from ENUM to VARCHAR(30)...");
                jdbcTemplate.execute("ALTER TABLE loans MODIFY COLUMN status VARCHAR(30) NOT NULL");
                log.info("Successfully converted loans.status to VARCHAR(30)");
            } else {
                log.debug("loans.status is already VARCHAR, no migration needed");
            }
        } catch (Exception e) {
            log.warn("Could not migrate loans.status column: {}. This may be expected on first run.", e.getMessage());
        }
    }

    /**
     * Add current_guarantor_index column to loans table for sequential guarantor approval.
     */
    private void addLoanGuarantorIndexColumn() {
        try {
            // Check if the loans table exists
            String checkTable = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'loans'";
            Integer tableExists = jdbcTemplate.queryForObject(checkTable, Integer.class);
            
            if (tableExists == null || tableExists == 0) {
                log.info("Loans table doesn't exist yet, skipping migration");
                return;
            }

            // Check if the column already exists
            String checkColumn = "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'loans' AND column_name = 'current_guarantor_index'";
            Integer columnExists = jdbcTemplate.queryForObject(checkColumn, Integer.class);
            
            if (columnExists == null || columnExists == 0) {
                log.info("Adding current_guarantor_index column to loans table...");
                jdbcTemplate.execute("ALTER TABLE loans ADD COLUMN current_guarantor_index INT DEFAULT 0");
                // Set existing approved guarantor counts as the index
                jdbcTemplate.update("UPDATE loans SET current_guarantor_index = COALESCE(guarantors_approved_count, 0)");
                log.info("Successfully added current_guarantor_index column to loans table");
            } else {
                log.debug("current_guarantor_index column already exists, no migration needed");
            }
        } catch (Exception e) {
            log.warn("Could not add current_guarantor_index column: {}. This may be expected on first run.", e.getMessage());
        }
    }

    /**
     * Fix loan_approvals.level column - make it NOT NULL and set default for existing NULL values.
     */
    private void fixLoanApprovalLevelColumn() {
        try {
            // Check if the loan_approvals table exists
            String checkTable = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'loan_approvals'";
            Integer tableExists = jdbcTemplate.queryForObject(checkTable, Integer.class);
            
            if (tableExists == null || tableExists == 0) {
                log.info("Loan_approvals table doesn't exist yet, skipping migration");
                return;
            }

            // Update existing NULL levels to 1 (for guarantor approvals that didn't have a level)
            log.info("Fixing NULL levels in loan_approvals...");
            int updated = jdbcTemplate.update("UPDATE loan_approvals SET level = 1 WHERE level IS NULL");
            if (updated > 0) {
                log.info("Updated {} loan_approvals with NULL level to 1", updated);
            }

            // Make the column NOT NULL
            String checkNullable = "SELECT IS_NULLABLE FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'loan_approvals' AND column_name = 'level'";
            String isNullable = jdbcTemplate.queryForObject(checkNullable, String.class);

            if ("YES".equalsIgnoreCase(isNullable)) {
                log.info("Making loan_approvals.level NOT NULL...");
                jdbcTemplate.execute("ALTER TABLE loan_approvals MODIFY COLUMN level INT NOT NULL");
                log.info("Successfully made loan_approvals.level NOT NULL");
            } else {
                log.debug("loan_approvals.level is already NOT NULL, no migration needed");
            }
        } catch (Exception e) {
            log.warn("Could not fix loan_approvals.level column: {}. This may be expected on first run.", e.getMessage());
        }
    }

    /**
     * Add first_login column to members table.
     * Default is true for new members (they must change password on first login).
     * Existing members get false (they've already logged in before).
     */
    private void addFirstLoginColumn() {
        try {
            // Check if the members table exists
            String checkTable = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'members'";
            Integer tableExists = jdbcTemplate.queryForObject(checkTable, Integer.class);
            
            if (tableExists == null || tableExists == 0) {
                log.info("Members table doesn't exist yet, skipping migration");
                return;
            }

            // Check if the column already exists
            String checkColumn = "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'members' AND column_name = 'first_login'";
            Integer columnExists = jdbcTemplate.queryForObject(checkColumn, Integer.class);
            
            if (columnExists == null || columnExists == 0) {
                log.info("Adding first_login column to members table...");
                jdbcTemplate.execute("ALTER TABLE members ADD COLUMN first_login BIT(1) DEFAULT 1");
                // Existing members are not first-time logins (they've already been using the system)
                jdbcTemplate.update("UPDATE members SET first_login = 0");
                log.info("Successfully added first_login column to members table");
            } else {
                log.debug("first_login column already exists, no migration needed");
            }
        } catch (Exception e) {
            log.warn("Could not add first_login column: {}. This may be expected on first run.", e.getMessage());
        }
    }

    /**
     * Create password_history table for tracking last 5 passwords.
     */
    private void createPasswordHistoryTable() {
        try {
            // Check if the password_history table already exists
            String checkTable = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'password_history'";
            Integer tableExists = jdbcTemplate.queryForObject(checkTable, Integer.class);
            
            if (tableExists != null && tableExists > 0) {
                log.debug("password_history table already exists, no migration needed");
                return;
            }

            log.info("Creating password_history table...");
            jdbcTemplate.execute("""
                CREATE TABLE password_history (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    member_id BIGINT NOT NULL,
                    password_hash VARCHAR(255) NOT NULL,
                    changed_at DATETIME NOT NULL,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    CONSTRAINT fk_password_history_member FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE,
                    INDEX idx_password_history_member (member_id),
                    INDEX idx_password_history_changed_at (changed_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
            log.info("Successfully created password_history table");
        } catch (Exception e) {
            log.warn("Could not create password_history table: {}. This may be expected on first run.", e.getMessage());
        }
    }

    /**
     * Fix audit_logs.member_id foreign key to SET NULL on delete.
     * This allows deleting members while preserving audit history.
     * The member_name and member_email columns are preserved in audit_logs.
     */
    private void fixAuditLogMemberForeignKey() {
        try {
            // Check if the audit_logs table exists
            String checkTable = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'audit_logs'";
            Integer tableExists = jdbcTemplate.queryForObject(checkTable, Integer.class);
            
            if (tableExists == null || tableExists == 0) {
                log.info("Audit_logs table doesn't exist yet, skipping migration");
                return;
            }

            // Check if the foreign key exists
            String checkFk = "SELECT CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS WHERE table_schema = DATABASE() AND table_name = 'audit_logs' AND constraint_type = 'FOREIGN KEY' AND CONSTRAINT_NAME LIKE '%member%'";
            String constraintName = null;
            try {
                constraintName = jdbcTemplate.queryForObject(checkFk, String.class);
            } catch (Exception e) {
                // No constraint found
            }

            if (constraintName != null) {
                // Check if it already has ON DELETE SET NULL
                String checkDeleteRule = "SELECT DELETE_RULE FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'audit_logs' AND CONSTRAINT_NAME = ?";
                String deleteRule = jdbcTemplate.queryForObject(checkDeleteRule, String.class, constraintName);
                
                if (!"SET NULL".equalsIgnoreCase(deleteRule)) {
                    log.info("Fixing audit_logs.member_id foreign key to SET NULL on delete...");
                    
                    // Drop the existing foreign key
                    jdbcTemplate.execute("ALTER TABLE audit_logs DROP FOREIGN KEY " + constraintName);
                    
                    // Add new foreign key with ON DELETE SET NULL
                    jdbcTemplate.execute("ALTER TABLE audit_logs ADD CONSTRAINT fk_audit_logs_member FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE SET NULL");
                    
                    log.info("Successfully fixed audit_logs.member_id foreign key");
                } else {
                    log.debug("audit_logs.member_id foreign key already has ON DELETE SET NULL, no migration needed");
                }
            } else {
                log.debug("No member_id foreign key found on audit_logs, no migration needed");
            }
        } catch (Exception e) {
            log.warn("Could not fix audit_logs.member_id foreign key: {}. This may be expected on first run.", e.getMessage());
        }
    }

    /**
     * Add category column to contribution_types table.
     * Categories: NORMAL, WELFARE, EMERGENCY, OTHER
     */
    private void addContributionTypeCategoryColumn() {
        try {
            // Check if the contribution_types table exists
            String checkTable = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'contribution_types'";
            Integer tableExists = jdbcTemplate.queryForObject(checkTable, Integer.class);
            
            if (tableExists == null || tableExists == 0) {
                log.info("Contribution_types table doesn't exist yet, skipping migration");
                return;
            }

            // Check if the column already exists
            String checkColumn = "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'contribution_types' AND column_name = 'category'";
            Integer columnExists = jdbcTemplate.queryForObject(checkColumn, Integer.class);
            
            if (columnExists == null || columnExists == 0) {
                log.info("Adding category column to contribution_types table...");
                jdbcTemplate.execute("ALTER TABLE contribution_types ADD COLUMN category VARCHAR(20) DEFAULT 'NORMAL'");
                // Set default for existing rows
                jdbcTemplate.update("UPDATE contribution_types SET category = 'NORMAL' WHERE category IS NULL");
                log.info("Successfully added category column to contribution_types table");
            } else {
                log.debug("category column already exists in contribution_types, no migration needed");
            }
        } catch (Exception e) {
            log.warn("Could not add category column to contribution_types: {}. This may be expected on first run.", e.getMessage());
        }
    }

    /**
     * Expand welfare_contributions.payment_method column to VARCHAR(20).
     * This allows the new MIXED value (5 chars) which exceeds ENUM/small VARCHAR constraints.
     */
    private void expandWelfareContributionPaymentMethodColumn() {
        try {
            // Check if the welfare_contributions table exists
            String checkTable = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'welfare_contributions'";
            Integer tableExists = jdbcTemplate.queryForObject(checkTable, Integer.class);
            
            if (tableExists == null || tableExists == 0) {
                log.info("Welfare_contributions table doesn't exist yet, skipping migration");
                return;
            }

            // Check if the payment_method column exists
            String checkColumn = "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'welfare_contributions' AND column_name = 'payment_method'";
            Integer columnExists = jdbcTemplate.queryForObject(checkColumn, Integer.class);
            
            if (columnExists == null || columnExists == 0) {
                log.info("payment_method column doesn't exist in welfare_contributions yet, skipping migration");
                return;
            }

            // Check current column type
            String checkType = "SELECT DATA_TYPE FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'welfare_contributions' AND column_name = 'payment_method'";
            String dataType = jdbcTemplate.queryForObject(checkType, String.class);

            if ("enum".equalsIgnoreCase(dataType)) {
                log.info("Converting welfare_contributions.payment_method from ENUM to VARCHAR(20)...");
                jdbcTemplate.execute("ALTER TABLE welfare_contributions MODIFY COLUMN payment_method VARCHAR(20) DEFAULT 'CASH'");
                log.info("Successfully converted welfare_contributions.payment_method to VARCHAR(20)");
            } else {
                // Check current column length for VARCHAR
                String checkLength = "SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'welfare_contributions' AND column_name = 'payment_method'";
                Integer currentLength = jdbcTemplate.queryForObject(checkLength, Integer.class);

                if (currentLength != null && currentLength < 20) {
                    log.info("Expanding welfare_contributions.payment_method from VARCHAR({}) to VARCHAR(20)...", currentLength);
                    jdbcTemplate.execute("ALTER TABLE welfare_contributions MODIFY COLUMN payment_method VARCHAR(20) DEFAULT 'CASH'");
                    log.info("Successfully expanded welfare_contributions.payment_method to VARCHAR(20)");
                } else {
                    log.debug("welfare_contributions.payment_method is already large enough, no migration needed");
                }
            }
        } catch (Exception e) {
            log.warn("Could not migrate welfare_contributions.payment_method column: {}. This may be expected on first run.", e.getMessage());
        }
    }

    /**
     * Add contribution_type_id column to transactions table.
     * This allows tracking which contribution type a transaction belongs to,
     * enabling balance calculation as: Credits - Debits per contribution type.
     */
    private void addContributionTypeToTransactions() {
        try {
            // Check if the transactions table exists
            String checkTable = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'transactions'";
            Integer tableExists = jdbcTemplate.queryForObject(checkTable, Integer.class);
            
            if (tableExists == null || tableExists == 0) {
                log.info("Transactions table doesn't exist yet, skipping migration");
                return;
            }

            // Check if the column already exists
            String checkColumn = "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'transactions' AND column_name = 'contribution_type_id'";
            Integer columnExists = jdbcTemplate.queryForObject(checkColumn, Integer.class);
            
            if (columnExists == null || columnExists == 0) {
                log.info("Adding contribution_type_id column to transactions table...");
                jdbcTemplate.execute("ALTER TABLE transactions ADD COLUMN contribution_type_id BIGINT NULL");
                jdbcTemplate.execute("ALTER TABLE transactions ADD CONSTRAINT fk_transactions_contribution_type FOREIGN KEY (contribution_type_id) REFERENCES contribution_types(id) ON DELETE SET NULL");
                log.info("Successfully added contribution_type_id column to transactions table");
                
                // Backfill existing CONTRIBUTION transactions by linking to the contribution record's type
                log.info("Backfilling contribution_type_id for existing CONTRIBUTION transactions...");
                int updated = jdbcTemplate.update("""
                    UPDATE transactions t
                    JOIN contributions c ON t.reference_id = c.id AND t.reference_type = 'CONTRIBUTION'
                    SET t.contribution_type_id = c.contribution_type_id
                    WHERE t.type = 'CONTRIBUTION' AND t.contribution_type_id IS NULL
                """);
                log.info("Backfilled {} CONTRIBUTION transactions with contribution_type_id", updated);
                
                // Also backfill CONTRIBUTION_TOPUP transactions
                updated = jdbcTemplate.update("""
                    UPDATE transactions t
                    JOIN contribution_top_ups tu ON t.reference_id = tu.contribution_id AND t.reference_type = 'CONTRIBUTION_TOPUP'
                    SET t.contribution_type_id = tu.contribution_type_id
                    WHERE t.type = 'CONTRIBUTION' AND t.contribution_type_id IS NULL
                """);
                log.info("Backfilled {} CONTRIBUTION_TOPUP transactions with contribution_type_id", updated);
            } else {
                log.debug("contribution_type_id column already exists in transactions, no migration needed");
            }
        } catch (Exception e) {
            log.warn("Could not add contribution_type_id column to transactions: {}. This may be expected on first run.", e.getMessage());
        }
    }

    /**
     * Backfill contribution_type_id for any CONTRIBUTION transactions that still have it NULL.
     * This runs every time to catch transactions that were created before the feature was added
     * or where the backfill didn't run properly.
     */
    private void backfillContributionTypeOnTransactions() {
        try {
            // Check if the transactions table exists
            String checkTable = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'transactions'";
            Integer tableExists = jdbcTemplate.queryForObject(checkTable, Integer.class);
            
            if (tableExists == null || tableExists == 0) {
                return;
            }

            // Count how many CONTRIBUTION transactions have null contribution_type_id
            String countNull = "SELECT COUNT(*) FROM transactions WHERE type = 'CONTRIBUTION' AND contribution_type_id IS NULL AND reference_type = 'CONTRIBUTION'";
            Integer nullCount = jdbcTemplate.queryForObject(countNull, Integer.class);
            
            if (nullCount != null && nullCount > 0) {
                log.info("Found {} CONTRIBUTION transactions with null contribution_type_id, backfilling...", nullCount);
                
                // Backfill from the contributions table
                int updated = jdbcTemplate.update("""
                    UPDATE transactions t
                    JOIN contributions c ON t.reference_id = c.id
                    SET t.contribution_type_id = c.contribution_type_id
                    WHERE t.type = 'CONTRIBUTION' 
                    AND t.reference_type = 'CONTRIBUTION'
                    AND t.contribution_type_id IS NULL
                    AND c.contribution_type_id IS NOT NULL
                """);
                log.info("Backfilled {} CONTRIBUTION transactions with contribution_type_id", updated);
            }
            
            // Also check for CONTRIBUTION_TOPUP transactions
            String countTopupNull = "SELECT COUNT(*) FROM transactions WHERE type = 'CONTRIBUTION' AND contribution_type_id IS NULL AND reference_type = 'CONTRIBUTION_TOPUP'";
            Integer topupNullCount = jdbcTemplate.queryForObject(countTopupNull, Integer.class);
            
            if (topupNullCount != null && topupNullCount > 0) {
                log.info("Found {} CONTRIBUTION_TOPUP transactions with null contribution_type_id, backfilling...", topupNullCount);
                
                int updated = jdbcTemplate.update("""
                    UPDATE transactions t
                    JOIN contribution_top_ups tu ON t.reference_id = tu.contribution_id
                    SET t.contribution_type_id = tu.contribution_type_id
                    WHERE t.type = 'CONTRIBUTION' 
                    AND t.reference_type = 'CONTRIBUTION_TOPUP'
                    AND t.contribution_type_id IS NULL
                    AND tu.contribution_type_id IS NOT NULL
                """);
                log.info("Backfilled {} CONTRIBUTION_TOPUP transactions with contribution_type_id", updated);
            }
        } catch (Exception e) {
            log.warn("Could not backfill contribution_type_id on transactions: {}", e.getMessage());
        }
    }

    /**
     * Add source_contribution_type_id column to welfare_contributions table if it doesn't exist.
     * This column tracks which contribution type funds were used to pay for welfare.
     */
    private void addSourceContributionTypeToWelfareContributions() {
        try {
            // Check if the welfare_contributions table exists
            String checkTable = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'welfare_contributions'";
            Integer tableExists = jdbcTemplate.queryForObject(checkTable, Integer.class);
            
            if (tableExists == null || tableExists == 0) {
                log.info("Welfare_contributions table doesn't exist yet, skipping migration");
                return;
            }

            // Check if the source_contribution_type_id column already exists
            String checkColumn = "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'welfare_contributions' AND column_name = 'source_contribution_type_id'";
            Integer columnExists = jdbcTemplate.queryForObject(checkColumn, Integer.class);
            
            if (columnExists == null || columnExists == 0) {
                log.info("Adding source_contribution_type_id column to welfare_contributions table...");
                jdbcTemplate.execute("ALTER TABLE welfare_contributions ADD COLUMN source_contribution_type_id BIGINT NULL");
                jdbcTemplate.execute("ALTER TABLE welfare_contributions ADD CONSTRAINT fk_welfare_contrib_source_type FOREIGN KEY (source_contribution_type_id) REFERENCES contribution_types(id) ON DELETE SET NULL");
                log.info("Successfully added source_contribution_type_id column to welfare_contributions table");
            } else {
                log.debug("source_contribution_type_id column already exists in welfare_contributions, no migration needed");
            }
            
            // Check if the source_contribution_id column exists (for completeness)
            String checkContribColumn = "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'welfare_contributions' AND column_name = 'source_contribution_id'";
            Integer contribColumnExists = jdbcTemplate.queryForObject(checkContribColumn, Integer.class);
            
            if (contribColumnExists == null || contribColumnExists == 0) {
                log.info("Adding source_contribution_id column to welfare_contributions table...");
                jdbcTemplate.execute("ALTER TABLE welfare_contributions ADD COLUMN source_contribution_id BIGINT NULL");
                jdbcTemplate.execute("ALTER TABLE welfare_contributions ADD CONSTRAINT fk_welfare_contrib_source_contrib FOREIGN KEY (source_contribution_id) REFERENCES contributions(id) ON DELETE SET NULL");
                log.info("Successfully added source_contribution_id column to welfare_contributions table");
            } else {
                log.debug("source_contribution_id column already exists in welfare_contributions, no migration needed");
            }
        } catch (Exception e) {
            log.warn("Could not add source columns to welfare_contributions: {}. This may be expected on first run.", e.getMessage());
        }
    }
}
