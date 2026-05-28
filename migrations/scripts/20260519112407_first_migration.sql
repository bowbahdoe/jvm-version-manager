-- // First migration.
CREATE SCHEMA system;


CREATE FUNCTION system.set_current_timestamp_updated_at()
    RETURNS TRIGGER AS
$$
DECLARE
    _new record;
BEGIN
    _new := NEW;
    _new."updated_at" = NOW();
    RETURN _new;
END;
$$ LANGUAGE plpgsql;


CREATE SCHEMA identity;


CREATE TABLE identity."user"
(
    id                       uuid        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    profile_image_png_base64 text,
    atproto_did              text        not null unique,
    atproto_handle           text        not null unique,
    created_at               timestamptz not null default now(),
    updated_at               timestamptz not null default now()
);

CREATE TRIGGER set_identity_user_updated_at
    BEFORE UPDATE
    ON identity.user
    FOR EACH ROW
EXECUTE PROCEDURE system.set_current_timestamp_updated_at();

CREATE TABLE identity.organization
(
    id         uuid        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

CREATE TRIGGER set_identity_organization_updated_at
    BEFORE UPDATE
    ON identity.organization
    FOR EACH ROW
EXECUTE PROCEDURE system.set_current_timestamp_updated_at();

CREATE TABLE identity.organization_user
(
    id              uuid        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    user_id         uuid        NOT NULL references identity."user" (id),
    organization_id uuid        NOT NULL references identity.organization (id)
        on update restrict
        on delete restrict,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now()
);

CREATE TRIGGER set_identity_organization_user_updated_at
    BEFORE UPDATE
    ON identity.organization_user
    FOR EACH ROW
EXECUTE PROCEDURE system.set_current_timestamp_updated_at();

CREATE SCHEMA qa;

CREATE TABLE qa.question
(
    id                         uuid        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    asked_by_user_id           uuid        not null,
    asked_on_behalf_of_user_id uuid,
    title                      text        not null,
    message                    text        not null default '',
    created_at                 timestamptz not null default now(),
    updated_at                 timestamptz not null default now()
);

CREATE TRIGGER set_qa_question_updated_at
    BEFORE UPDATE
    ON qa.question
    FOR EACH ROW
EXECUTE PROCEDURE system.set_current_timestamp_updated_at();

CREATE TABLE qa.tag
(
    id                 uuid        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    value              text        not null,
    created_by_user_id uuid references identity.user (id)
        on update restrict
        on delete restrict,
    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now()
);

CREATE TRIGGER set_qa_tag_updated_at
    BEFORE UPDATE
    ON qa.tag
    FOR EACH ROW
EXECUTE PROCEDURE system.set_current_timestamp_updated_at();

CREATE TABLE qa.question_tag
(
    id          uuid        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    question_id uuid        not null,
    tag_id      uuid        not null,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);

CREATE TRIGGER set_qa_question_tag_updated_at
    BEFORE UPDATE
    ON qa.question_tag
    FOR EACH ROW
EXECUTE PROCEDURE system.set_current_timestamp_updated_at();

INSERT INTO qa.tag(value)
VALUES ('java'),
       ('spring'),
       ('code review'),
       ('other');

CREATE SCHEMA repository;


CREATE TABLE repository.artifact
(
    id         uuid        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    sha256     text,
    data       bytea       not null,
    unique (sha256),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

CREATE TRIGGER set_repository_artifact_updated_at
    BEFORE UPDATE
    ON repository.artifact
    FOR EACH ROW
EXECUTE PROCEDURE system.set_current_timestamp_updated_at();

--;
CREATE TABLE IF NOT EXISTS repository.provider
(
    id         uuid        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    name       text        not null unique,
    synthetic  boolean     not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

COMMENT ON COLUMN repository.provider.synthetic
    IS 'Whether the provider was "made up" in the system and there is no actual user who represents the provider';

CREATE TRIGGER set_repository_provider_updated_at
    BEFORE UPDATE
    ON repository.provider
    FOR EACH ROW
EXECUTE PROCEDURE system.set_current_timestamp_updated_at();
--;
CREATE TABLE IF NOT EXISTS repository.provider_maven
(
    id             uuid        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    provider_id    uuid        not null,
    mvn_groupId    text        not null,
    mvn_repository text        not null,
    created_at     timestamptz not null default now(),
    updated_at     timestamptz not null default now(),
    unique (mvn_groupId, mvn_repository),
    FOREIGN KEY (provider_id) REFERENCES repository.provider (id)
        on update restrict
        on delete restrict
);
CREATE TRIGGER set_repository_provider_maven_updated_at
    BEFORE UPDATE
    ON repository.provider_maven
    FOR EACH ROW
EXECUTE PROCEDURE system.set_current_timestamp_updated_at();
--;
CREATE TABLE IF NOT EXISTS repository.module
(
    id              uuid        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    name            text        not null,
    version         text        not null,
    target_platform text        not null,
    provider_id     uuid,
    mandated        boolean     not null,
    synthetic       boolean     not null,
    module_info     text        not null,
    mvn_repository  text,
    mvn_groupId     text,
    mvn_artifactId  text,
    mvn_version     text,
    mvn_classifier  text,
    type            text,
    sha256          text        not null unique,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    unique (name, version, target_platform, provider_id),
    FOREIGN KEY (sha256) REFERENCES repository.artifact (sha256)
        on update restrict
        on delete restrict
);
CREATE TRIGGER set_repository_module_updated_at
    BEFORE UPDATE
    ON repository.module
    FOR EACH ROW
EXECUTE PROCEDURE system.set_current_timestamp_updated_at();
--;
CREATE TABLE IF NOT EXISTS repository.module_provides
(
    id        uuid NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    module_id uuid not null,
    service   text not null,
    "with"    text not null,
    unique (module_id, service, "with"),
    FOREIGN KEY (module_id) REFERENCES repository.module (id)
        on update restrict
        on delete restrict
);
--;
CREATE TABLE IF NOT EXISTS repository.module_uses
(
    id        uuid NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    module_id uuid not null,
    service   text not null,
    unique (module_id, service),
    FOREIGN KEY (module_id) REFERENCES repository.module (id)
        on update restrict
        on delete restrict
);
--;
CREATE TABLE IF NOT EXISTS repository.module_requires
(
    id         uuid    NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    module_id  uuid    not null,
    module     text    not null,
    version    text,
    static     boolean not null,
    transitive boolean not null,
    mandated   boolean not null,
    synthetic  boolean not null,
    unique (module_id, module),
    FOREIGN KEY (module_id) REFERENCES repository.module (id)
        on update restrict
        on delete restrict
);
--;
CREATE TABLE IF NOT EXISTS repository.module_exports
(
    id        uuid    NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    module_id uuid    not null,
    package   text    not null,
    "to"      text,
    mandated  boolean not null,
    synthetic boolean not null,
    unique (module_id, package, "to"),
    FOREIGN KEY (module_id) REFERENCES repository.module (id)
        on update restrict
        on delete restrict
);
--;
CREATE TABLE IF NOT EXISTS repository.module_package
(
    id        uuid NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    module_id uuid not null,
    package   text not null,
    unique (module_id, package),
    FOREIGN KEY (module_id) REFERENCES repository.module (id)
        on update restrict
        on delete restrict
);
--;
CREATE TABLE IF NOT EXISTS repository.module_hash
(
    id        uuid NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    module_id uuid not null,
    module    text not null,
    algorithm text not null,
    hash      text not null,
    unique (module_id, module, algorithm),
    FOREIGN KEY (module_id) REFERENCES repository.module (id)
        on update restrict
        on delete restrict
);
--;
CREATE TABLE IF NOT EXISTS repository.module_set
(
    id          uuid        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    name        text        not null,
    provider_id uuid,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    FOREIGN KEY (provider_id) REFERENCES repository.provider (id)
        on update restrict
        on delete restrict
);

CREATE TRIGGER set_repository_module_set_updated_at
    BEFORE UPDATE
    ON repository.module_set
    FOR EACH ROW
EXECUTE PROCEDURE system.set_current_timestamp_updated_at();
--;
CREATE TABLE IF NOT EXISTS repository.module_set_element
(
    id            uuid        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    module_set_id uuid        not null,
    module_id     uuid        not null,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now(),
    unique (module_set_id, module_id),
    FOREIGN KEY (module_set_id) REFERENCES repository.module_set (id)
        on update restrict
        on delete restrict,
    FOREIGN KEY (module_id) REFERENCES repository.module (id)
        on update restrict
        on delete restrict
);

CREATE TRIGGER set_repository_module_set_element_updated_at
    BEFORE UPDATE
    ON repository.module_set_element
    FOR EACH ROW
EXECUTE PROCEDURE system.set_current_timestamp_updated_at();

--;
-- A module set published for the consumption of
-- other users.
CREATE TABLE IF NOT EXISTS repository.published_module_set
(
    id            uuid        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    module_set_id uuid        not null,
    provider_id   uuid        not null,
    name          text        not null,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now(),
    FOREIGN KEY (module_set_id) REFERENCES repository.module_set (id)
        on update restrict
        on delete restrict,
    FOREIGN KEY (provider_id) REFERENCES repository.provider (id)
        on update restrict
        on delete restrict,
    UNIQUE (module_set_id, provider_id)
);


CREATE TRIGGER set_repository_published_module_set_updated_at
    BEFORE UPDATE
    ON repository.published_module_set
    FOR EACH ROW
EXECUTE PROCEDURE system.set_current_timestamp_updated_at();
--;
-- Grants permission to a publisher to publish modules
-- with a name. I.E. if we grant oracle
-- the ability to publish modules with the java. prefix
-- they can do so, but if a provider does not have that
-- permission they cannot.
--
-- This can also be permission to publish an exact module.
-- If so, the prefix column will be set to false.
CREATE TABLE IF NOT EXISTS repository.provider_module_permission
(
    id          uuid        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    provider_id uuid        not null,
    module      text        not null,
    -- If true, this permission is for publishing an *exact*
    -- module name, not for a specific prefix.
    prefix      boolean     not null default true,
    -- Set to a time when/if permission to publish modules under a prefix
    -- is revoked
    revoked_at  timestamptz,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    FOREIGN KEY (provider_id) REFERENCES repository.provider (id)
        on update restrict
        on delete restrict,
    UNIQUE (provider_id, prefix)
);

CREATE TRIGGER set_repository_provider_module_permission_updated_at
    BEFORE UPDATE
    ON repository.provider_module_permission
    FOR EACH ROW
EXECUTE PROCEDURE system.set_current_timestamp_updated_at();
--;
-- Automatically ingest and track an artifact from maven.
-- New versions should be discovered by a background process;
CREATE TABLE IF NOT EXISTS repository.maven_tracked_artifact
(
    id             uuid        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    mvn_repository text        not null,
    mvn_groupId    text        not null,
    mvn_artifactId text        not null,
    -- Provider to assign ingested artifacts to
    provider_id    uuid,
    -- Only ingest artifacts after the given version
    start_version  text,
    created_at     timestamptz not null default now(),
    updated_at     timestamptz not null default now(),
    FOREIGN KEY (provider_id) REFERENCES repository.provider (id)
        on update restrict
        on delete restrict
);

CREATE TRIGGER set_repository_maven_tracked_artifact_updated_at
    BEFORE UPDATE
    ON repository.maven_tracked_artifact
    FOR EACH ROW
EXECUTE PROCEDURE system.set_current_timestamp_updated_at();
--;
CREATE TABLE IF NOT EXISTS repository.maven_ingestion_job
(
    id                        uuid        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    maven_tracked_artifact_id uuid,
    mvn_repository            text        not null,
    mvn_groupId               text        not null,
    mvn_artifactId            text        not null,
    mvn_version               text        not null,
    mvn_classifier            text        not null,
    mvn_type                  text        not null,
    started_at                text        not null default current_timestamp,
    finished_at               text        not null default current_timestamp,
    error                     text,
    created_at                timestamptz not null default now(),
    updated_at                timestamptz not null default now(),
    FOREIGN KEY (maven_tracked_artifact_id) REFERENCES repository.maven_tracked_artifact (id)
        on update restrict
        on delete restrict
);

CREATE TRIGGER set_repository_maven_ingestion_job_updated_at
    BEFORE UPDATE
    ON repository.maven_ingestion_job
    FOR EACH ROW
EXECUTE PROCEDURE system.set_current_timestamp_updated_at();
--;
CREATE TABLE IF NOT EXISTS repository.maven_ingestion_job_module
(
    id                     uuid        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    maven_ingestion_job_id uuid        not null,
    module_id              uuid        not null,
    created_at             timestamptz not null default now(),
    updated_at             timestamptz not null default now(),
    FOREIGN KEY (maven_ingestion_job_id) REFERENCES repository.maven_ingestion_job (id)
        on update restrict
        on delete restrict,
    FOREIGN KEY (module_id) REFERENCES repository.module (id)
        on update restrict
        on delete restrict
);

CREATE TRIGGER set_repository_maven_ingestion_job_module_updated_at
    BEFORE UPDATE
    ON repository.maven_ingestion_job_module
    FOR EACH ROW
EXECUTE PROCEDURE system.set_current_timestamp_updated_at();
--;
CREATE TABLE IF NOT EXISTS repository.jdk_ingestion_job
(
    id                       uuid        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    windows_amd64_url        text,
    windows_amd64_sha256_url text,
    windows_amd64_sha256     text,
    macos_aarch64_url        text,
    macos_aarch64_sha256_url text,
    macos_aarch64_sha256     text,
    linux_aarch64_url        text,
    linux_aarch64_sha256_url text,
    linux_aarch64_sha256     text,
    linux_amd64_url          text,
    linux_amd64_sha256_url   text,
    linux_amd64_sha256       text,
    provider_id              uuid        not null,
    started_at               text,
    finished_at              text,
    error                    text,
    created_at               timestamptz not null default now(),
    updated_at               timestamptz not null default now(),
    FOREIGN KEY (provider_id) REFERENCES repository.provider (id)
        on update restrict
        on delete restrict
);

CREATE TRIGGER set_repository_jdk_ingestion_job_updated_at
    BEFORE UPDATE
    ON repository.maven_ingestion_job_module
    FOR EACH ROW
EXECUTE PROCEDURE system.set_current_timestamp_updated_at();

-- //@UNDO

DROP TABLE repository.jdk_ingestion_job;

DROP TABLE repository.maven_ingestion_job_module;

DROP TABLE repository.maven_ingestion_job;

DROP TABLE repository.maven_tracked_artifact;

DROP TABLE repository.provider_module_permission;

DROP TABLE repository.published_module_set;

DROP TABLE repository.module_set_element;

DROP TABLE repository.module_set;

DROP TABLE repository.module_hash;

DROP TABLE repository.module_package;

DROP TABLE repository.module_exports;

DROP TABLE repository.module_requires;

DROP TABLE repository.module_uses;

DROP TABLE repository.module_provides;

DROP TABLE repository.module;

DROP TABLE repository.provider_maven;

DROP TABLE repository.provider;

DROP TABLE repository.artifact;

DROP SCHEMA repository;


DROP TABLE qa.question_tag;
DROP TABLE qa.tag;
DROP TABLE qa.question;

DROP SCHEMA qa;

DROP TABLE identity.organization_user;
DROP TABLE identity.organization;
DROP TABLE identity.user;


DROP SCHEMA identity;


DROP FUNCTION system.set_current_timestamp_updated_at;

DROP SCHEMA system;