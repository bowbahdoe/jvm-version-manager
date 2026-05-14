CREATE TABLE IF NOT EXISTS artifact(
    id integer primary key,
    sha256 text unique,
    data blob not null,
    unique (sha256)
);
--;
CREATE TABLE IF NOT EXISTS provider(
    id integer primary key,
    name text not null unique,
    -- Whether the provider was "made up" in the system
    -- and there is no actual user who represents the provider
    synthetic boolean not null
);
--;
CREATE TABLE IF NOT EXISTS provider_maven(
    id integer primary key,
    provider_id integer not null,
    mvn_groupId text not null,
    mvn_repository text not null,
    unique (mvn_groupId, mvn_repository),
    FOREIGN KEY (provider_id) REFERENCES provider(id)
);
--;
CREATE TABLE IF NOT EXISTS module_set(
    id integer primary key,
    name text not null,
    version text not null,
    description text not null,
    provider_id integer,
    FOREIGN KEY (provider_id) REFERENCES provider(id),
    UNIQUE (name, version)
);
--;
CREATE TABLE IF NOT EXISTS module_set_element(
    id integer primary key,
    module_set_id integer not null,
    module_id integer not null,
    unique (module_set_id, module_id),
    FOREIGN KEY (module_set_id) REFERENCES module_set(id),
    FOREIGN KEY (module_id) REFERENCES module(id)
);
--;
CREATE TABLE IF NOT EXISTS module(
    id integer primary key,
    name text not null,
    version text not null,
    target_platform text not null,
    provider_id integer,
    mandated boolean not null,
    synthetic boolean not null,
    module_info text not null,
    mvn_repository text,
    mvn_groupId text,
    mvn_artifactId text,
    mvn_version text,
    mvn_classifier text,
    type text,
    sha256 text not null unique,
    created_at TEXT NOT NULL DEFAULT current_timestamp,
    unique (name, version, target_platform, provider_id),
    FOREIGN KEY(sha256) REFERENCES artifact(sha256)
);
--;
CREATE TABLE IF NOT EXISTS module_provides(
    id integer primary key,
    module_id integer not null,
    service text not null,
    "with" text not null,
    unique (module_id, service, "with"),
    FOREIGN KEY(module_id) REFERENCES module(id)
);
--;
CREATE TABLE IF NOT EXISTS module_uses(
    id integer primary key,
    module_id integer not null,
    service text not null,
    unique (module_id, service),
    FOREIGN KEY(module_id) REFERENCES module(id)
);
--;
CREATE TABLE IF NOT EXISTS module_requires(
    id integer primary key,
    module_id integer not null,
    module text not null,
    version text,
    static boolean not null,
    transitive boolean not null,
    mandated boolean not null,
    synthetic boolean not null,
    unique (module_id, module),
    FOREIGN KEY(module_id) REFERENCES module(id)
);
--;
CREATE TABLE IF NOT EXISTS module_exports(
    id integer primary key,
    module_id integer not null,
    package text not null,
    "to" text,
    mandated boolean not null,
    synthetic boolean not null,
    unique (module_id, package, "to"),
    FOREIGN KEY(module_id) REFERENCES module(id)
);
--;
CREATE TABLE IF NOT EXISTS module_package(
    id integer primary key,
    module_id integer not null,
    package text not null,
    unique (module_id, package),
    FOREIGN KEY(module_id) REFERENCES module(id)
);
--;
CREATE TABLE IF NOT EXISTS module_hash(
    id integer primary key,
    module_id integer not null,
    module    text not null,
    algorithm text not null,
    hash      text not null,
    unique (module_id, module, algorithm),
    FOREIGN KEY(module_id) REFERENCES module(id)
);
--;
-- A module set published for the consumption of
-- other users.
CREATE TABLE IF NOT EXISTS published_module_set(
    id integer primary key,
    module_set_id integer not null,
    provider_id integer not null,
    name text not null,
    FOREIGN KEY(module_set_id) REFERENCES module_set(id),
    FOREIGN KEY(provider_id) REFERENCES provider(id),
    UNIQUE (module_set_id, provider_id)
);
--;
-- Grants permission to a publisher to publish modules
-- with a name. I.E. if we grant oracle
-- the ability to publish modules with the java. prefix
-- they can do so, but if a provider does not have that
-- permission they cannot.
--
-- This can also be permission to publish an exact module.
-- If so, the prefix column will be set to false.
CREATE TABLE IF NOT EXISTS provider_module_permission(
    id integer primary key,
    provider_id integer not null,
    module text not null,
    -- If true, this permission is for publishing an *exact*
    -- module name, not for a specific prefix.
    prefix boolean not null default true,
    -- Set to a time when/if permission to publish modules under a prefix
    -- is revoked
    revoked_at TEXT,
    FOREIGN KEY (provider_id) REFERENCES provider(id),
    UNIQUE (provider_id, prefix)
);
--;
-- Automatically ingest and track an artifact from maven.
-- New versions should be discovered by a background process;
CREATE TABLE IF NOT EXISTS maven_tracked_artifact(
    id integer primary key,
    mvn_repository text not null,
    mvn_groupId text not null,
    mvn_artifactId text not null,
    -- Provider to assign ingested artifacts to
    provider_id integer,
    -- Only ingest artifacts after the given version
    start_version text
);
--;
CREATE TABLE IF NOT EXISTS maven_ingestion_job(
    id integer primary key,
    maven_tracked_artifact_id integer,
    mvn_repository text not null,
    mvn_groupId text not null,
    mvn_artifactId text not null,
    mvn_version text not null,
    mvn_classifier text not null,
    mvn_type text not null,
    started_at text not null default current_timestamp,
    finished_at text not null default current_timestamp,
    error text,
    FOREIGN KEY (maven_tracked_artifact_id) REFERENCES maven_tracked_artifact(id)
);
--;
CREATE TABLE IF NOT EXISTS maven_ingestion_job_module(
    id integer primary key,
    maven_ingestion_job_id integer not null,
    module_id integer not null,
    FOREIGN KEY(maven_ingestion_job_id) REFERENCES maven_ingestion_job(id),
    FOREIGN KEY(module_id) REFERENCES module(id)
);
--;
CREATE TABLE IF NOT EXISTS jdk_ingestion_job(
    id integer primary key,
    windows_amd64_url text,
    windows_amd64_sha256_url text,
    windows_amd64_sha256 text,
    macos_aarch64_url text,
    macos_aarch64_sha256_url text,
    macos_aarch64_sha256 text,
    linux_aarch64_url text,
    linux_aarch64_sha256_url text,
    linux_aarch64_sha256 text,
    linux_amd64_url text,
    linux_amd64_sha256_url text,
    linux_amd64_sha256 text,
    provider_id integer not null,
    started_at text,
    finished_at text,
    error text,
    FOREIGN KEY (provider_id) REFERENCES provider(id)
);

