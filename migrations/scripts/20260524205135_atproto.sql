-- // atproto
CREATE SCHEMA atproto;

CREATE TABLE atproto.jetstream_event(
    id uuid not null default uuidv7(),
    event jsonb not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

CREATE TRIGGER set_atproto_jetstream_event_updated_at
    BEFORE UPDATE
    ON atproto.jetstream_event
    FOR EACH ROW
EXECUTE PROCEDURE system.set_current_timestamp_updated_at();

CREATE OR REPLACE FUNCTION atproto_jetstream_event_processEvent_insert_function()
    RETURNS TRIGGER AS
$$
BEGIN
    INSERT INTO proletarian.job(job_type, payload)
    VALUES (':atproto.jetstream_event/processEvent', row_to_json(NEW));
    return NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER atproto_jetstream_event_processEvent_insert_trigger
    AFTER INSERT
    ON atproto.jetstream_event
    FOR EACH ROW
EXECUTE PROCEDURE atproto_jetstream_event_processEvent_insert_function();


-- //@UNDO
DROP TRIGGER identity_user_genProfileImage_insert_trigger ON identity.user;
DROP FUNCTION identity_user_genProfileImage_insert_function;



DROP TRIGGER set_atproto_jetstream_event_updated_at ON atproto.jetstream_event;
DROP TABLE atproto.jetstream_event;
DROP SCHEMA atproto;


