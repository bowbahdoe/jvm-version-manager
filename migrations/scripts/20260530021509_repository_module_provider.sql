-- // repository module provider

CREATE TABLE atproto.record
(
    id         uuid        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    did        text        not null,
    collection text        not null,
    rkey       text        not null,
    rev        text        not null,
    cid        text        not null,
    record     jsonb       not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (did, collection, rkey)
);

CREATE TRIGGER set_atproto_record_updated_at
    BEFORE UPDATE
    ON atproto.record
    FOR EACH ROW
EXECUTE PROCEDURE system.set_current_timestamp_updated_at();

CREATE TABLE atproto.dev_mccue_jvm_module
(
    id                uuid        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    atproto_record_id uuid        not null unique
        references atproto.record (id) on delete cascade,

    index_me          boolean     not null default false,
    record_created_at timestamptz not null,

    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    unique (atproto_record_id)
);

CREATE TRIGGER set_atproto_dev_mccue_jvm_module_updated_at
    BEFORE UPDATE
    ON atproto.dev_mccue_jvm_module
    FOR EACH ROW
EXECUTE PROCEDURE system.set_current_timestamp_updated_at();

CREATE TABLE atproto.dev_mccue_jvm_module_variant
(
    id                      uuid        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    dev_mccue_jvm_module_id uuid        not null references atproto.dev_mccue_jvm_module (id)
        on delete cascade,
    license                 text,
    sourced_from_url        text,
    sourced_from_cid        text,
    sourced_from_aturi      text,
    artifact_cid_link       text        not null,
    artifact_size           bigint      not null,
    created_at              timestamptz not null default now(),
    updated_at              timestamptz not null default now()
);

-- We will relate modules via CID
CREATE INDEX ON atproto.dev_mccue_jvm_module_variant (artifact_cid_link);

CREATE TRIGGER set_atproto_dev_mccue_jvm_module_variant_updated_at
    BEFORE UPDATE
    ON atproto.dev_mccue_jvm_module_variant
    FOR EACH ROW
EXECUTE PROCEDURE system.set_current_timestamp_updated_at();


ALTER TABLE atproto.dev_mccue_jvm_module_variant
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

CREATE TABLE atproto.dev_mccue_jvm_module_variant_error
(
    id                              uuid        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    dev_mccue_jvm_module_variant_id uuid        not null references atproto.dev_mccue_jvm_module_variant (id)
        on delete cascade,
    error                           text        not null,
    created_at                      timestamptz not null default now(),
    updated_at                      timestamptz not null default now()
);

CREATE TRIGGER set_atproto_dev_mccue_jvm_module_variant_error_updated_at
    BEFORE UPDATE
    ON atproto.dev_mccue_jvm_module_variant_error
    FOR EACH ROW
EXECUTE PROCEDURE system.set_current_timestamp_updated_at();

CREATE TABLE atproto.dev_mccue_jvm_module_variant_attribute
(
    id                              uuid        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    dev_mccue_jvm_module_variant_id uuid        not null references atproto.dev_mccue_jvm_module_variant (id)
        on delete cascade,
    name                            text        not null,
    value                           text        not null,
    created_at                      timestamptz not null default now(),
    updated_at                      timestamptz not null default now(),
    unique (name, value)
);

CREATE TRIGGER set_atproto_dev_mccue_jvm_module_variant_attribute_updated_at
    BEFORE UPDATE
    ON atproto.dev_mccue_jvm_module_variant_attribute
    FOR EACH ROW
EXECUTE PROCEDURE system.set_current_timestamp_updated_at();

CREATE TABLE IF NOT EXISTS repository.published_module
(
    id          uuid        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    module_id   uuid        not null references repository.module (id),
    atproto_did text        not null,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);

CREATE TRIGGER set_repository_published_module_updated_at
    BEFORE UPDATE
    ON repository.published_module
    FOR EACH ROW
EXECUTE PROCEDURE system.set_current_timestamp_updated_at();

-- // insert trigger
CREATE FUNCTION atproto_record_processModule_function()
    RETURNS TRIGGER AS
$$
BEGIN
    INSERT INTO proletarian.job(job_type, payload)
    VALUES (':atproto.record/processModule', row_to_json(NEW));
    return NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER atproto_record_processModule_trigger
    AFTER INSERT OR UPDATE
    ON atproto.record
    FOR EACH ROW
EXECUTE PROCEDURE atproto_record_processModule_function();


CREATE FUNCTION atproto_dev_mccue_jvm_module_importModule_function()
    RETURNS TRIGGER AS
$$
BEGIN
    INSERT INTO proletarian.job(job_type, payload)
    VALUES (':atproto.dev_mccue_jvm_module/importModule', row_to_json(NEW));
    return NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER atproto_dev_mccue_jvm_module_importModule_trigger
    AFTER INSERT
    ON atproto.dev_mccue_jvm_module
    FOR EACH ROW
EXECUTE PROCEDURE atproto_dev_mccue_jvm_module_importModule_function();

-- //@UNDO
DROP TRIGGER atproto_record_processModule_trigger ON atproto.record;
DROP FUNCTION atproto_record_processModule_function;

DROP TRIGGER atproto_dev_mccue_jvm_module_importModule_trigger ON atproto.dev_mccue_jvm_module;
DROP FUNCTION atproto_dev_mccue_jvm_module_importModule_function;

DROP TABLE repository.published_module;
DROP TABLE atproto.dev_mccue_jvm_module_variant_error;
DROP TABLE atproto.dev_mccue_jvm_module_variant;
DROP TABLE atproto.dev_mccue_jvm_module;

DROP TABLE atproto.record;


