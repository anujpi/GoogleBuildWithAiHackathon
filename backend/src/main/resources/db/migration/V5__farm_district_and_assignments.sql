-- MASTER_SPEC §5.1 V5. A farm's canonical district (nullable: farms may lie outside the scope; existing rows stay
-- NULL, nothing is guessed), and the districts an admin assigns to FPO / AGRICULTURAL_OFFICER users.

ALTER TABLE farm ADD COLUMN district_id varchar(50) REFERENCES ref_district (district_id);
CREATE INDEX farm_district_idx ON farm (district_id);

CREATE TABLE user_district_assignment (
    user_id     uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    district_id varchar(50) NOT NULL REFERENCES ref_district (district_id),
    assigned_at timestamptz NOT NULL,
    assigned_by uuid        REFERENCES users (id),
    PRIMARY KEY (user_id, district_id)
);
