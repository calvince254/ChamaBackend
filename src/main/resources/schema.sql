-- Fix contribution status column to support all enum values
-- This runs on startup if spring.sql.init.mode is set to 'always'
ALTER TABLE contributions MODIFY COLUMN status VARCHAR(20) NOT NULL;

-- Fix penalty status column if needed
ALTER TABLE penalties MODIFY COLUMN status VARCHAR(20) NOT NULL;

-- Fix merry-go-round rounds status column to support all enum values (PENDING, PARTIAL, COMPLETED, PAID)
ALTER TABLE merry_go_round_rounds MODIFY COLUMN status VARCHAR(20) NOT NULL;
