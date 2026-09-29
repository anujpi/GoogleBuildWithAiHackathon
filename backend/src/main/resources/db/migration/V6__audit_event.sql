-- MASTER_SPEC §5.1 V6. Append-only audit log. `details` never holds passwords, tokens or secrets.

CREATE TABLE audit_event (
    id            uuid        PRIMARY KEY,
    occurred_at   timestamptz NOT NULL,
    actor_user_id uuid        REFERENCES users (id),
    action        varchar(40) NOT NULL CHECK (action IN (
        'USER_REGISTERED', 'LOGIN_SUCCEEDED', 'LOGIN_FAILED', 'ROLE_CHANGED', 'USER_ENABLED', 'USER_DISABLED',
        'DISTRICTS_ASSIGNED', 'FARM_CREATED', 'FARM_UPDATED', 'FARM_OWNER_ASSIGNED', 'REFERENCE_SYNCED',
        'ADMIN_BOOTSTRAPPED')),
    target_type   varchar(30),
    target_id     varchar(64),
    details       jsonb       NOT NULL DEFAULT '{}',
    request_id    varchar(64)
);

CREATE INDEX audit_event_occurred_at_idx ON audit_event (occurred_at DESC);
CREATE INDEX audit_event_actor_idx ON audit_event (actor_user_id);
