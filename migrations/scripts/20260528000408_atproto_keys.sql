-- // atproto keys
CREATE TABLE atproto.access_credential(
    id uuid not null default uuidv7(),
    did text not null unique,
    access_token text not null,
    refresh_token text not null,
    service_endpoint text not null,
    dpop_private_key text not null,
    scopes text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

CREATE TRIGGER set_atproto_access_credential_event_updated_at
    BEFORE UPDATE
    ON atproto.access_credential
    FOR EACH ROW
EXECUTE PROCEDURE system.set_current_timestamp_updated_at();



-- //@UNDO

DROP TABLE atproto.access_credential;

