-- Version bền cho nhập lô, quyết định và cấu hình của run được giữ trong phiên.
ALTER TABLE observation ADD COLUMN input_seq BIGINT UNSIGNED NOT NULL DEFAULT 0,
  ADD COLUMN payload_json JSON NULL,
  ADD COLUMN logical_sha256 CHAR(64) NULL,
  ADD UNIQUE INDEX uq_observation_logical (logical_sha256);
ALTER TABLE sign_history ADD COLUMN input_seq BIGINT UNSIGNED NOT NULL DEFAULT 0,
  ADD COLUMN run_id VARCHAR(160) NULL,
  ADD INDEX ix_history_run (run_id, sign_id);
ALTER TABLE review ADD COLUMN input_seq BIGINT UNSIGNED NOT NULL DEFAULT 0;
ALTER TABLE snapshot_run ADD COLUMN config_yaml LONGTEXT NULL,
  ADD COLUMN classes_json JSON NULL;
ALTER TABLE sign_snapshot ADD COLUMN sign_version INT UNSIGNED NOT NULL DEFAULT 1;
