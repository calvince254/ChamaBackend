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
}
