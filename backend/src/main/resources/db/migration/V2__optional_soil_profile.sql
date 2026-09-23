-- A farm may exist without soil data; absence means "unavailable", never a filled-in default.
ALTER TABLE farm ALTER COLUMN soil_profile_id DROP NOT NULL;
