package com.calpex_technologies.chama.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Handles database schema migrations that Hibernate's ddl-auto can't do.
 * For example, converting ENUM columns to VARCHAR.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseMigration {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void migrate() {
        convertFinesStatusToVarchar();
        expandInvestmentCategoryColumn();
        convertTransactionsTypeToVarchar();
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
}
