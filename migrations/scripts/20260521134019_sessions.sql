-- // sessions
CREATE TABLE IF NOT EXISTS identity.session
(
    id             uuid        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    data           jsonb       not null default '{}',
    expires_at     timestamptz not null default now(),
    invalidated_at timestamptz,
    created_at     timestamptz not null default now(),
    updated_at     timestamptz not null default now()
);

CREATE TRIGGER set_session_updated_at
    BEFORE UPDATE
    ON identity.session
    FOR EACH ROW
EXECUTE PROCEDURE system.set_current_timestamp_updated_at();


-- //@UNDO

DROP TABLE identity.session;


