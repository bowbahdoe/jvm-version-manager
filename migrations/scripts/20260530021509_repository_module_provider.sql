-- // repository module provider
--;
CREATE TABLE IF NOT EXISTS repository.module_provider
(
    id          uuid        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    module_name text        not null,
    atproto_did text        not null,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    unique (module_name, atproto_did)
);

CREATE TRIGGER set_repository_module_provider_updated_at
    BEFORE UPDATE
    ON repository.module_provider
    FOR EACH ROW
EXECUTE PROCEDURE system.set_current_timestamp_updated_at();


-- //@UNDO
DROP TABLE repository.module_provider;


