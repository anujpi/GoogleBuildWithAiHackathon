-- MASTER_SPEC §5.1 V7 / D12. Agronomic limits per crop, from FAO EcoCrop only, with a citation.
--
-- BLOCKER B2: the EcoCrop values have NOT been transcribed yet. Every numeric column is therefore NULL, and
-- source_url / retrieved_on are NULL. A NULL limit means "not available", never an estimate: crop evidence and
-- risk report the dependent factors (soil pH, temperature) as UNAVAILABLE until a person fills them in.
-- To fill them: add a NEW migration (never edit this one) that UPDATEs each row with the EcoCrop values,
-- source_url and retrieved_on. Values that EcoCrop does not publish stay NULL.

-- The four D2 crops, so the foreign key holds on a clean database before any ML sync (the sync upserts them).
INSERT INTO ref_crop (crop_id, label) VALUES
    ('potato', 'Potato'),
    ('wheat', 'Wheat'),
    ('onion', 'Onion'),
    ('maize', 'Maize')
ON CONFLICT DO NOTHING;

CREATE TABLE crop_requirement (
    crop_id        varchar(50)  PRIMARY KEY REFERENCES ref_crop (crop_id),
    ph_abs_min     numeric,
    ph_opt_min     numeric,
    ph_opt_max     numeric,
    ph_abs_max     numeric,
    temp_abs_min_c numeric,
    temp_opt_min_c numeric,
    temp_opt_max_c numeric,
    temp_abs_max_c numeric,
    source         varchar(100) NOT NULL DEFAULT 'FAO EcoCrop',
    source_url     varchar(500),
    retrieved_on   date
);

INSERT INTO crop_requirement (crop_id) VALUES ('potato'), ('wheat'), ('onion'), ('maize');
