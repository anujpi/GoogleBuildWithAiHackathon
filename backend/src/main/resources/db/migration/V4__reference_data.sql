-- Reference scope synced from the ML service (MASTER_SPEC §5.1, §6.3). ML is the authority for these ids (D3);
-- the backend only mirrors them. Rows are upserted by the sync and never deleted: farms may reference them.

CREATE TABLE ref_state (
    state_id varchar(50)  PRIMARY KEY,
    label    varchar(100) NOT NULL
);

CREATE TABLE ref_district (
    district_id varchar(50)  PRIMARY KEY,
    state_id    varchar(50)  NOT NULL REFERENCES ref_state (state_id),
    label       varchar(100) NOT NULL
);

CREATE TABLE ref_crop (
    crop_id varchar(50)  PRIMARY KEY,
    label   varchar(100) NOT NULL
);

-- One reported district x crop x season production series in the served ML artifact.
CREATE TABLE ref_supply_series (
    district_id    varchar(50) NOT NULL REFERENCES ref_district (district_id),
    crop_id        varchar(50) NOT NULL REFERENCES ref_crop (crop_id),
    season         varchar(16) NOT NULL
        CHECK (season IN ('KHARIF', 'RABI', 'SUMMER', 'WHOLE_YEAR', 'AUTUMN', 'WINTER')),
    first_year     int         NOT NULL,
    last_year      int         NOT NULL,
    years_observed int         NOT NULL,
    PRIMARY KEY (district_id, crop_id, season),
    CHECK (first_year <= last_year)
);

CREATE TABLE ref_dataset (
    source          varchar(100) NOT NULL,
    dataset_version varchar(100) NOT NULL,
    data_through    varchar(10)  NOT NULL,
    PRIMARY KEY (source, dataset_version)
);

-- Sync history: one row per attempt, successful or not.
CREATE TABLE reference_sync (
    id               uuid         PRIMARY KEY,
    synced_at        timestamptz  NOT NULL,
    status           varchar(16)  NOT NULL CHECK (status IN ('SUCCEEDED', 'FAILED')),
    ml_model_version varchar(100),
    message          varchar(500)
);

CREATE INDEX reference_sync_synced_at_idx ON reference_sync (synced_at DESC);
