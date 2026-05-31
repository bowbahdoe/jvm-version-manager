-- // repository module provider
--;
CREATE TABLE IF NOT EXISTS repository.jetstream_module
(
    id                uuid        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    record            jsonb       not null,
    provider_did      text        not null,
    rkey              text        not null,
    record_created_at timestamptz not null default now(),
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now()
);

CREATE TRIGGER set_repository_jetstream_module_updated_at
    BEFORE UPDATE
    ON repository.jetstream_module
    FOR EACH ROW
EXECUTE PROCEDURE system.set_current_timestamp_updated_at();

CREATE TABLE IF NOT EXISTS repository.jetstream_module_variant
(
    id                                 uuid        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    jetstream_module_id                uuid        not null references repository.jetstream_module (id)
        on update restrict
        on delete restrict,
    artifact_cid                       text        not null,
    license                            text,
    bill_of_materials                  text,
    cpu_architecture                   text,
    operating_system                   text,
    sourced_from_url                   text,
    sourced_from_cid                   text,
    sourced_from_aturi                text,

    number_of_module_infos_in_artifact integer,

    -- Does the record rkey test.example:123 match what is actually in the module info?
    rkey_module_name                   text,
    artifact_module_name               text,
    module_name_matches                boolean,

    rkey_module_version                text,
    artifact_module_version            text,
    module_version_matches             boolean,

    -- Is target platform metadata in the module believable?
    artifact_target_platform           text,

    -- license_string_is_well_formed      boolean,

    -- If all validations pass, we put it into our table of modules
    -- which is where we can get metadata like requires/exports/etc. from.
    module_id                          uuid references repository.module (id),
    created_at                         timestamptz not null default now(),
    updated_at                         timestamptz not null default now()
);

ALTER TABLE repository.jetstream_module_variant
    ADD CONSTRAINT source_is_url_or_hardref
        CHECK ((
                   sourced_from_url IS NULL AND
                   sourced_from_cid IS NULL AND
                   sourced_from_aturi IS NULL
                   ) OR (
                   sourced_from_url IS NOT NULL AND
                   sourced_from_cid IS NULL AND
                   sourced_from_aturi IS NULL
                   ) OR (
                   sourced_from_url IS NULL AND
                   sourced_from_cid IS NOT NULL AND
                   sourced_from_aturi IS NOT NULL
                   ));

CREATE TRIGGER set_repository_jetstream_module_variant_updated_at
    BEFORE UPDATE
    ON repository.jetstream_module_variant
    FOR EACH ROW
EXECUTE PROCEDURE system.set_current_timestamp_updated_at();

CREATE TABLE IF NOT EXISTS repository.published_module
(
    id                          uuid        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    module_info                 text,
    module_name                 text        not null,
    module_version              text        not null,
    jetstream_module_variant_id uuid        not null references repository.jetstream_module_variant (id)
        on update restrict on delete restrict,
    created_at                  timestamptz not null default now(),
    updated_at                  timestamptz not null default now()
);

CREATE TRIGGER set_repository_published_module_updated_at
    BEFORE UPDATE
    ON repository.published_module
    FOR EACH ROW
EXECUTE PROCEDURE system.set_current_timestamp_updated_at();

-- // insert trigger
CREATE OR REPLACE FUNCTION repository_jetstream_module_variant_checkModule_insert_function()
    RETURNS TRIGGER AS
$$
BEGIN
    INSERT INTO proletarian.job(job_type, payload)
    VALUES (':repository.jetstream_module_variant/checkModule', row_to_json(NEW));
    return NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER repository_jetstream_module_variant_checkModule_insert_trigger
    AFTER INSERT
    ON repository.jetstream_module_variant
    FOR EACH ROW
EXECUTE PROCEDURE repository_jetstream_module_variant_checkModule_insert_function();

-- //@UNDO
DROP TRIGGER repository_jetstream_module_variant_checkModule_insert_trigger ON repository.jetstream_module_variant;
DROP FUNCTION repository_jetstream_module_variant_checkModule_insert_function;

DROP TABLE repository.published_module;
DROP TABLE repository.jetstream_module_variant;
DROP TABLE repository.jetstream_module;


