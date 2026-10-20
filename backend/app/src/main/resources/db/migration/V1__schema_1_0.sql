-- Tám bảng nghiệp vụ theo docs/contracts/db-schema.md, DDL 1.0.
-- Danh mục sign_type nạp từ ai/configs/classes.yaml, không ghi danh sách mã ở đây.
SET time_zone = '+00:00';

CREATE TABLE `user` (
  user_id VARCHAR(160) PRIMARY KEY,
  username VARCHAR(80) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  role ENUM('user','importer','admin') NOT NULL DEFAULT 'user',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE vehicle_profile (
  vehicle_profile_id VARCHAR(160) PRIMARY KEY,
  user_id VARCHAR(160) NOT NULL,
  vehicle_type ENUM('car','truck','motorcycle') NOT NULL,
  display_name VARCHAR(120) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_vehicle_user FOREIGN KEY (user_id) REFERENCES `user`(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE sign_type (
  sign_type_code VARCHAR(16) PRIMARY KEY,
  class_id SMALLINT UNSIGNED NOT NULL UNIQUE,
  classes_version VARCHAR(64) NOT NULL,
  name_vi VARCHAR(160) NOT NULL,
  sign_group ENUM('prohibition','warning','information') NOT NULL,
  vehicle_types JSON NOT NULL,
  CONSTRAINT ck_type_vehicles CHECK (JSON_TYPE(vehicle_types) = 'ARRAY')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE trip (
  trip_id VARCHAR(160) PRIMARY KEY,
  source ENUM('mapillary','android_replay') NOT NULL,
  sequence_id VARCHAR(128) NOT NULL,
  creator_id VARCHAR(128) NULL,
  user_id VARCHAR(160) NOT NULL,
  started_at DATETIME(3) NULL,
  ended_at DATETIME(3) NULL,
  trace_ref VARCHAR(512) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  INDEX ix_trip_source_sequence (source, sequence_id),
  INDEX ix_trip_creator (creator_id),
  CONSTRAINT fk_trip_user FOREIGN KEY (user_id) REFERENCES `user`(user_id),
  CONSTRAINT ck_trip_time CHECK (ended_at IS NULL OR started_at IS NULL OR ended_at >= started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE sign (
  sign_id VARCHAR(160) PRIMARY KEY,
  sign_type_code VARCHAR(16) NOT NULL,
  location POINT NOT NULL SRID 4326,
  sign_facing_deg DOUBLE NULL,
  road_ref VARCHAR(255) NULL,
  status ENUM('candidate','confirmed_auto','confirmed_manual','unclear',
              'suspected_change','removed','replaced') NOT NULL DEFAULT 'candidate',
  replacement_sign_id VARCHAR(160) NULL,
  as_of_year SMALLINT UNSIGNED NULL,
  alpha DECIMAL(18,9) NOT NULL DEFAULT 1,
  beta DECIMAL(18,9) NOT NULL DEFAULT 1,
  agreement_score DECIMAL(10,9) NOT NULL DEFAULT 0.5,
  positive_pair_count INT UNSIGNED NOT NULL DEFAULT 0,
  negative_pair_count INT UNSIGNED NOT NULL DEFAULT 0,
  last_seen_at DATETIME(3) NULL,
  last_evidence_at DATETIME(3) NULL,
  conflict_reasons JSON NOT NULL,
  params_version VARCHAR(64) NOT NULL,
  version INT UNSIGNED NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  SPATIAL INDEX sx_sign_location (location),
  INDEX ix_sign_state_type (status, sign_type_code),
  CONSTRAINT fk_sign_type FOREIGN KEY (sign_type_code) REFERENCES sign_type(sign_type_code),
  CONSTRAINT fk_sign_replacement FOREIGN KEY (replacement_sign_id) REFERENCES sign(sign_id),
  CONSTRAINT ck_sign_direction CHECK (sign_facing_deg IS NULL OR (sign_facing_deg >= 0 AND sign_facing_deg < 360)),
  CONSTRAINT ck_sign_score CHECK (alpha >= 1 AND beta >= 1 AND agreement_score BETWEEN 0 AND 1),
  CONSTRAINT ck_sign_replacement CHECK (replacement_sign_id IS NULL OR replacement_sign_id <> sign_id),
  CONSTRAINT ck_sign_replaced CHECK (status <> 'replaced' OR replacement_sign_id IS NOT NULL),
  CONSTRAINT ck_sign_conflicts CHECK (JSON_TYPE(conflict_reasons) = 'ARRAY')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE observation (
  observation_id VARCHAR(160) PRIMARY KEY,
  schema_version VARCHAR(16) NOT NULL,
  classes_version VARCHAR(64) NOT NULL,
  payload_sha256 CHAR(64) NOT NULL,
  source ENUM('mapillary','android_replay') NOT NULL,
  trip_id VARCHAR(160) NOT NULL,
  sequence_id VARCHAR(128) NOT NULL,
  creator_id VARCHAR(128) NULL,
  captured_at DATETIME(3) NOT NULL,
  captured_at_precision ENUM('instant','day') NOT NULL,
  captured_year SMALLINT UNSIGNED GENERATED ALWAYS AS (YEAR(captured_at)) STORED,
  image_id VARCHAR(128) NOT NULL,
  target_sign_id VARCHAR(160) NULL,
  source_sign_id VARCHAR(128) NULL,
  source_sign_type VARCHAR(160) NULL,
  sign_type_code VARCHAR(16) NULL,
  camera_lon DOUBLE NULL,
  camera_lat DOUBLE NULL,
  camera_heading_deg DOUBLE NULL,
  sign_lon DOUBLE NULL,
  sign_lat DOUBLE NULL,
  sign_facing_deg DOUBLE NULL,
  distance_m DOUBLE NULL,
  off_deg DOUBLE NULL,
  is_pano BOOLEAN NULL,
  focal DOUBLE NULL,
  image_width_px INT UNSIGNED NULL,
  image_height_px INT UNSIGNED NULL,
  bbox_px JSON NULL,
  detector_confidence DOUBLE NULL,
  outcome ENUM('seen','not_seen','unknown') NOT NULL,
  in_fov BOOLEAN NULL,
  facing_camera BOOLEAN NULL,
  sharp BOOLEAN NULL,
  unoccluded BOOLEAN NULL,
  visibility_method ENUM('unverified','independent_model_audited','human','automatic_rules') NOT NULL,
  model_version VARCHAR(128) NULL,
  evidence_ref VARCHAR(512) NULL,
  source_metadata JSON NULL,
  ingest_status ENUM('accepted','metadata_only') NOT NULL,
  uploaded_by VARCHAR(160) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  INDEX ix_observation_pair (target_sign_id, creator_id, captured_year, outcome, captured_at),
  INDEX ix_observation_time (captured_at),
  INDEX ix_observation_image (source, image_id),
  CONSTRAINT fk_observation_trip FOREIGN KEY (trip_id) REFERENCES trip(trip_id),
  CONSTRAINT fk_observation_sign FOREIGN KEY (target_sign_id) REFERENCES sign(sign_id),
  CONSTRAINT fk_observation_type FOREIGN KEY (sign_type_code) REFERENCES sign_type(sign_type_code),
  CONSTRAINT fk_observation_uploader FOREIGN KEY (uploaded_by) REFERENCES `user`(user_id),
  CONSTRAINT ck_observation_conf CHECK (detector_confidence IS NULL OR detector_confidence BETWEEN 0 AND 1),
  CONSTRAINT ck_observation_camera CHECK (
    (camera_lon IS NULL AND camera_lat IS NULL) OR
    (camera_lon IS NOT NULL AND camera_lat IS NOT NULL AND camera_lon BETWEEN -180 AND 180 AND camera_lat BETWEEN -90 AND 90)),
  CONSTRAINT ck_observation_sign_coord CHECK (
    (sign_lon IS NULL AND sign_lat IS NULL) OR
    (sign_lon IS NOT NULL AND sign_lat IS NOT NULL AND sign_lon BETWEEN -180 AND 180 AND sign_lat BETWEEN -90 AND 90)),
  CONSTRAINT ck_observation_heading CHECK (camera_heading_deg IS NULL OR (camera_heading_deg >= 0 AND camera_heading_deg < 360)),
  CONSTRAINT ck_observation_facing CHECK (sign_facing_deg IS NULL OR (sign_facing_deg >= 0 AND sign_facing_deg < 360)),
  CONSTRAINT ck_observation_off CHECK (off_deg IS NULL OR (off_deg >= -180 AND off_deg < 180)),
  CONSTRAINT ck_observation_distance CHECK (distance_m IS NULL OR distance_m >= 0),
  CONSTRAINT ck_observation_focal CHECK (focal IS NULL OR focal > 0),
  CONSTRAINT ck_observation_image_size CHECK (
    (image_width_px IS NULL AND image_height_px IS NULL) OR
    (image_width_px IS NOT NULL AND image_height_px IS NOT NULL AND image_width_px > 0 AND image_height_px > 0)),
  CONSTRAINT ck_observation_metadata_only CHECK (ingest_status <> 'metadata_only' OR outcome = 'unknown'),
  CONSTRAINT ck_observation_seen CHECK (outcome <> 'seen' OR
    (sign_type_code IS NOT NULL AND bbox_px IS NOT NULL AND detector_confidence IS NOT NULL
     AND model_version IS NOT NULL AND image_width_px IS NOT NULL AND image_height_px IS NOT NULL)),
  CONSTRAINT ck_observation_negative CHECK (outcome <> 'not_seen' OR
    (target_sign_id IS NOT NULL AND sign_type_code IS NOT NULL AND creator_id IS NOT NULL
     AND camera_lon IS NOT NULL AND camera_lat IS NOT NULL AND camera_heading_deg IS NOT NULL
     AND sign_lon IS NOT NULL AND sign_lat IS NOT NULL AND sign_facing_deg IS NOT NULL
     AND distance_m IS NOT NULL AND distance_m <= 30
     AND in_fov IS TRUE AND facing_camera IS TRUE AND sharp IS TRUE AND unoccluded IS TRUE
     AND bbox_px IS NULL AND detector_confidence IS NULL AND model_version IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE sign_history (
  history_id VARCHAR(160) PRIMARY KEY,
  sign_id VARCHAR(160) NOT NULL,
  event_type ENUM('state_changed','type_changed','admin_decision','score_updated') NOT NULL,
  old_status VARCHAR(32) NULL,
  new_status VARCHAR(32) NOT NULL,
  old_sign_type_code VARCHAR(16) NULL,
  new_sign_type_code VARCHAR(16) NOT NULL,
  before_snapshot JSON NULL,
  after_snapshot JSON NOT NULL,
  before_observation_id VARCHAR(160) NULL,
  after_observation_id VARCHAR(160) NULL,
  changed_by VARCHAR(160) NULL,
  effective_at DATETIME(3) NOT NULL,
  recorded_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  reason TEXT NOT NULL,
  INDEX ix_history_sign_time (sign_id, effective_at, recorded_at),
  CONSTRAINT fk_history_sign FOREIGN KEY (sign_id) REFERENCES sign(sign_id),
  CONSTRAINT fk_history_old_type FOREIGN KEY (old_sign_type_code) REFERENCES sign_type(sign_type_code),
  CONSTRAINT fk_history_new_type FOREIGN KEY (new_sign_type_code) REFERENCES sign_type(sign_type_code),
  CONSTRAINT fk_history_before FOREIGN KEY (before_observation_id) REFERENCES observation(observation_id),
  CONSTRAINT fk_history_after FOREIGN KEY (after_observation_id) REFERENCES observation(observation_id),
  CONSTRAINT fk_history_user FOREIGN KEY (changed_by) REFERENCES `user`(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE review (
  review_id VARCHAR(160) PRIMARY KEY,
  sign_id VARCHAR(160) NOT NULL,
  status ENUM('pending','decided') NOT NULL DEFAULT 'pending',
  conflict_reasons JSON NOT NULL,
  before_observation_id VARCHAR(160) NULL,
  after_observation_id VARCHAR(160) NULL,
  decision ENUM('confirm','keep','remove','replace','defer') NULL,
  replacement_sign_id VARCHAR(160) NULL,
  reporter_id VARCHAR(160) NULL,
  decided_by VARCHAR(160) NULL,
  reason TEXT NULL,
  effective_at DATETIME(3) NULL,
  decided_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  version INT UNSIGNED NOT NULL DEFAULT 1,
  pending_sign_id VARCHAR(160) GENERATED ALWAYS AS (CASE WHEN status = 'pending' THEN sign_id ELSE NULL END) STORED,
  UNIQUE KEY uq_review_pending (pending_sign_id),
  INDEX ix_review_queue (status, created_at, review_id),
  CONSTRAINT fk_review_sign FOREIGN KEY (sign_id) REFERENCES sign(sign_id),
  CONSTRAINT fk_review_before FOREIGN KEY (before_observation_id) REFERENCES observation(observation_id),
  CONSTRAINT fk_review_after FOREIGN KEY (after_observation_id) REFERENCES observation(observation_id),
  CONSTRAINT fk_review_replacement FOREIGN KEY (replacement_sign_id) REFERENCES sign(sign_id),
  CONSTRAINT fk_review_reporter FOREIGN KEY (reporter_id) REFERENCES `user`(user_id),
  CONSTRAINT fk_review_admin FOREIGN KEY (decided_by) REFERENCES `user`(user_id),
  CONSTRAINT ck_review_replace CHECK (decision <> 'replace' OR
    (replacement_sign_id IS NOT NULL AND replacement_sign_id <> sign_id)),
  CONSTRAINT ck_review_decided CHECK (status <> 'decided' OR
    (decision IS NOT NULL AND decision <> 'defer' AND decided_by IS NOT NULL AND decided_at IS NOT NULL
     AND effective_at IS NOT NULL AND reason IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
