-- Per-product personalisation (overrides the category's). NULL = use the category setting.
ALTER TABLE products ADD COLUMN customisation_spec VARCHAR(500);
