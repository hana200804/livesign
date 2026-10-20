-- Bảy cột bổ sung vào trip theo db-schema.md, Thay đổi 1.1. Mọi cột nullable/default an toàn.
ALTER TABLE trip
  ADD COLUMN trace_schema_version VARCHAR(16) NULL,
  ADD COLUMN trace_manifest_ref VARCHAR(512) NULL,
  ADD COLUMN trace_sha256 CHAR(64) NULL,
  ADD COLUMN point_count INT UNSIGNED NULL,
  ADD COLUMN data_kind ENUM('public_metadata','illustration') NULL,
  ADD COLUMN data_revision VARCHAR(64) NULL,
  ADD COLUMN replay_enabled BOOLEAN NOT NULL DEFAULT FALSE,
  ADD CONSTRAINT ck_trip_trace_sha256 CHECK (trace_sha256 IS NULL OR CHAR_LENGTH(trace_sha256) = 64);
