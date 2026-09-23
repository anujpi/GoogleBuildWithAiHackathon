CREATE EXTENSION IF NOT EXISTS postgis;

-- Measurements use unconstrained numeric so submitted values are stored exactly (no silent rounding).

CREATE TABLE farm_location (
    id            uuid PRIMARY KEY,
    latitude      numeric      NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude     numeric      NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    state         varchar(100) NOT NULL,
    district      varchar(100) NOT NULL,
    taluk         varchar(100),
    address_label varchar(255),
    -- Derived from latitude/longitude (the source of truth) for spatial queries; not mapped by JPA.
    geog          geography(Point, 4326) GENERATED ALWAYS AS (
                      ST_SetSRID(ST_MakePoint(longitude::float8, latitude::float8), 4326)::geography
                  ) STORED
);

CREATE INDEX farm_location_geog_idx ON farm_location USING gist (geog);

CREATE TABLE soil_profile (
    id                      uuid PRIMARY KEY,
    ph                      numeric CHECK (ph BETWEEN 0 AND 14),
    electrical_conductivity numeric CHECK (electrical_conductivity >= 0),
    organic_carbon          numeric CHECK (organic_carbon >= 0),
    nitrogen                numeric CHECK (nitrogen >= 0),
    phosphorus              numeric CHECK (phosphorus >= 0),
    potassium               numeric CHECK (potassium >= 0),
    sulphur                 numeric CHECK (sulphur >= 0),
    zinc                    numeric CHECK (zinc >= 0),
    iron                    numeric CHECK (iron >= 0),
    manganese               numeric CHECK (manganese >= 0),
    copper                  numeric CHECK (copper >= 0),
    boron                   numeric CHECK (boron >= 0),
    source                  varchar(32) NOT NULL
        CHECK (source IN ('SOIL_HEALTH_CARD', 'LAB_REPORT', 'MANUAL', 'REGIONAL_ESTIMATE', 'OTHER')),
    data_classification     varchar(16) NOT NULL
        CHECK (data_classification IN ('OBSERVED', 'ESTIMATED', 'SYNTHETIC')),
    measured_at             date,
    confidence              numeric CHECK (confidence BETWEEN 0 AND 1)
);

CREATE TABLE farm (
    id              uuid PRIMARY KEY,
    name            varchar(200) NOT NULL,
    area            numeric      NOT NULL CHECK (area > 0),
    area_unit       varchar(16)  NOT NULL CHECK (area_unit IN ('ACRE', 'HECTARE')),
    irrigation_type varchar(16)  NOT NULL
        CHECK (irrigation_type IN ('RAIN_FED', 'DRIP', 'SPRINKLER', 'CANAL', 'BOREWELL', 'OTHER')),
    current_crop    varchar(100),
    previous_crop   varchar(100),
    season          varchar(16)  NOT NULL CHECK (season IN ('KHARIF', 'RABI', 'ZAID', 'OTHER')),
    location_id     uuid         NOT NULL UNIQUE REFERENCES farm_location (id),
    soil_profile_id uuid         NOT NULL UNIQUE REFERENCES soil_profile (id),
    created_at      timestamptz  NOT NULL,
    updated_at      timestamptz  NOT NULL
);

-- Backs the default newest-first farm listing.
CREATE INDEX farm_created_at_idx ON farm (created_at DESC);
