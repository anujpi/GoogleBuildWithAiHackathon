-- State-level historical crop area and production, used to build ML supply-forecast requests.
-- Rows come from real published datasets only (see V5__load_kaggle_crop_yield); none are generated.
CREATE TABLE production_history (
    id                  uuid         PRIMARY KEY,
    state               varchar(100) NOT NULL,
    -- Crop and season labels are stored exactly as the source publishes them (the ML model expects them).
    crop                varchar(100) NOT NULL,
    season              varchar(32)  NOT NULL,
    crop_year           integer      NOT NULL CHECK (crop_year BETWEEN 1900 AND 2100),
    area_hectares       numeric      NOT NULL CHECK (area_hectares > 0),
    production_tonnes   numeric      NOT NULL CHECK (production_tonnes >= 0),
    source              varchar(32)  NOT NULL CHECK (source IN ('KAGGLE_CROP_YIELD')),
    data_classification varchar(16)  NOT NULL
        CHECK (data_classification IN ('OBSERVED', 'ESTIMATED', 'SYNTHETIC')),
    dataset_version     varchar(64)  NOT NULL
);

-- One value per source, series and year. Lookups are case-insensitive, so uniqueness is too.
CREATE UNIQUE INDEX production_history_series_year_key
    ON production_history (source, lower(state), lower(crop), season, crop_year);
