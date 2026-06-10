ALTER TABLE users
  ADD COLUMN acquisition_source VARCHAR(100) NULL AFTER synthetic,
  ADD COLUMN acquisition_campaign VARCHAR(100) NULL AFTER acquisition_source;
