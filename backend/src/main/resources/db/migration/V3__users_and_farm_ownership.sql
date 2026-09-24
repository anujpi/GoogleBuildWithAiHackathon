-- "user" is a reserved word in PostgreSQL, hence "users".
CREATE TABLE users (
    id            uuid         PRIMARY KEY,
    full_name     varchar(200) NOT NULL,
    email         varchar(254) NOT NULL,
    password_hash varchar(100) NOT NULL,
    role          varchar(32)  NOT NULL
        CHECK (role IN ('FARMER', 'FPO', 'AGRICULTURAL_OFFICER', 'ADMIN')),
    enabled       boolean      NOT NULL,
    created_at    timestamptz  NOT NULL,
    updated_at    timestamptz  NOT NULL,
    CONSTRAINT users_email_key UNIQUE (email),
    -- The application stores emails lower-cased, so uniqueness is effectively case-insensitive.
    CONSTRAINT users_email_lowercase CHECK (email = lower(email))
);

-- Farms created before accounts existed have no known owner. None is invented: owner_id stays NULL and,
-- because every farm query is scoped to the caller, those rows are preserved but unreachable via the API.
-- ponytail: nullable only for legacy rows; SET NOT NULL once they are claimed or deleted.
ALTER TABLE farm ADD COLUMN owner_id uuid REFERENCES users (id);

-- Farm listing is now always "this owner's farms, newest first".
CREATE INDEX farm_owner_created_at_idx ON farm (owner_id, created_at DESC);
DROP INDEX farm_created_at_idx;
