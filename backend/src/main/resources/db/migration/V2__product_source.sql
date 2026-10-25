-- Products can be managed from the Excel sheet (EXCEL) or from the admin screen (ADMIN).
-- The Excel sync only creates/updates/hides EXCEL products.
ALTER TABLE products ADD COLUMN source VARCHAR(10) DEFAULT 'EXCEL' NOT NULL;
