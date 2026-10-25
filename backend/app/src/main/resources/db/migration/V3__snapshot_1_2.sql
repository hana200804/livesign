-- Snapshot dựng sẵn theo năm, db-schema.md Thay đổi 1.2. Tám bảng 1.0 giữ nguyên.

CREATE TABLE data_state (
  id TINYINT UNSIGNED PRIMARY KEY,
  input_seq BIGINT UNSIGNED NOT NULL,
  CONSTRAINT ck_data_state_single CHECK (id = 1)
) ENGINE=InnoDB;

-- Một dòng duy nhất; input_seq tăng trong cùng transaction với lô có dòng mới hoặc quyết định admin.
INSERT INTO data_state (id, input_seq) VALUES (1, 0);

CREATE TABLE snapshot_run (
  run_id VARCHAR(160) PRIMARY KEY,
  evaluation_year SMALLINT UNSIGNED NOT NULL,
  cutoff_exclusive_utc DATETIME(3) NOT NULL,
  params_version VARCHAR(64) NOT NULL,
  config_sha256 CHAR(64) NOT NULL,
  classes_version VARCHAR(64) NOT NULL,
  data_revision VARCHAR(64) NOT NULL,
  input_seq BIGINT UNSIGNED NOT NULL,
  core_version VARCHAR(64) NOT NULL,
  status ENUM('building','ready','superseded','failed') NOT NULL DEFAULT 'building',
  sign_count INT UNSIGNED NULL,
  error_code VARCHAR(64) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  ready_at DATETIME(3) NULL,
  INDEX ix_run_lookup (evaluation_year, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE sign_snapshot (
  run_id VARCHAR(160) NOT NULL,
  sign_id VARCHAR(160) NOT NULL,
  sign_type_code VARCHAR(16) NOT NULL,
  location POINT NOT NULL SRID 4326,
  location_method ENUM('ray_least_squares','mapillary_feature','camera_proxy') NOT NULL,
  sign_facing_deg DOUBLE NULL,
  facing_source ENUM('aggregated','single_view','insufficient') NOT NULL,
  ray_count SMALLINT UNSIGNED NOT NULL DEFAULT 0,
  ray_residual_m DOUBLE NULL,
  source_sign_id VARCHAR(160) NULL,
  road_ref VARCHAR(255) NULL,
  status ENUM('candidate','confirmed_auto','confirmed_manual','unclear',
              'suspected_change','removed','replaced') NOT NULL,
  last_confirmation_source ENUM('auto','manual') NULL,
  replacement_sign_id VARCHAR(160) NULL,
  alpha DECIMAL(18,9) NOT NULL,
  beta DECIMAL(18,9) NOT NULL,
  agreement_score DECIMAL(10,9) NOT NULL,
  positive_pair_count INT UNSIGNED NOT NULL,
  negative_pair_count INT UNSIGNED NOT NULL,
  last_seen_at DATETIME(3) NULL,
  last_evidence_at DATETIME(3) NULL,
  conflict_reasons JSON NOT NULL,
  PRIMARY KEY (run_id, sign_id),
  SPATIAL INDEX sx_snapshot_location (location),
  INDEX ix_snapshot_status (run_id, status, sign_type_code),
  CONSTRAINT fk_snapshot_run FOREIGN KEY (run_id) REFERENCES snapshot_run(run_id),
  CONSTRAINT fk_snapshot_sign FOREIGN KEY (sign_id) REFERENCES sign(sign_id),
  CONSTRAINT fk_snapshot_type FOREIGN KEY (sign_type_code) REFERENCES sign_type(sign_type_code),
  CONSTRAINT fk_snapshot_replacement FOREIGN KEY (replacement_sign_id) REFERENCES sign(sign_id),
  CONSTRAINT ck_snapshot_replaced CHECK (status <> 'replaced' OR replacement_sign_id IS NOT NULL),
  CONSTRAINT ck_snapshot_facing CHECK (sign_facing_deg IS NULL OR (sign_facing_deg >= 0 AND sign_facing_deg < 360)),
  CONSTRAINT ck_snapshot_facing_source CHECK (facing_source <> 'aggregated' OR sign_facing_deg IS NOT NULL),
  CONSTRAINT ck_snapshot_score CHECK (alpha >= 1 AND beta >= 1 AND agreement_score BETWEEN 0 AND 1),
  CONSTRAINT ck_snapshot_conflicts CHECK (JSON_TYPE(conflict_reasons) = 'ARRAY')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE sign_snapshot_observation (
  run_id VARCHAR(160) NOT NULL,
  observation_id VARCHAR(160) NOT NULL,
  sign_id VARCHAR(160) NOT NULL,
  PRIMARY KEY (run_id, observation_id),
  INDEX ix_snapshot_obs_sign (run_id, sign_id),
  CONSTRAINT fk_snapshot_obs_row FOREIGN KEY (run_id, sign_id) REFERENCES sign_snapshot(run_id, sign_id),
  CONSTRAINT fk_snapshot_obs_observation FOREIGN KEY (observation_id) REFERENCES observation(observation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
