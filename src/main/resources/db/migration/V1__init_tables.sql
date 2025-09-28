
CREATE TABLE IF NOT EXISTS data (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  element VARCHAR(100) NOT NULL,
  measurement VARCHAR(100) NOT NULL,
  sw_version VARCHAR(50),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_data (element, measurement, sw_version)
);

CREATE TABLE IF NOT EXISTS value_m60 (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  element VARCHAR(100) NOT NULL,
  measurement VARCHAR(100) NOT NULL,
  source_id VARCHAR(100) NOT NULL,
  data_id BIGINT NULL,
  collect_dt DATE NOT NULL,
  hour_of_day TINYINT NOT NULL,           -- 0~23
  value_json TEXT NOT NULL,
  inserted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX ix_m60_1 (collect_dt, hour_of_day),
  INDEX ix_m60_2 (element, measurement, source_id)
);

CREATE TABLE IF NOT EXISTS value_1d (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  element VARCHAR(100) NOT NULL,
  measurement VARCHAR(100) NOT NULL,
  source_id VARCHAR(100) NOT NULL,
  collect_dt DATE NOT NULL,
  value_json TEXT NOT NULL,
  aggregated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_1d (element, measurement, source_id, collect_dt),
  INDEX ix_1d_1 (collect_dt)
);
