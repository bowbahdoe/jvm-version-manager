CREATE TABLE IF NOT EXISTS artifact(
    id text primary key,
    sha256 text unique,
    data blob not null,
    unique (sha256)
);
--;
CREATE TABLE IF NOT EXISTS provider(
    id text primary key,
    name text not null unique,
    -- Whether the provider was "made up" in the system
    -- and there is no actual user who represents the provider
    synthetic boolean not null
);
--;
CREATE TABLE IF NOT EXISTS provider_maven(
    id text primary key,
    provider_id text not null,
    mvn_groupId text not null,
    mvn_repository text not null,
    unique (mvn_groupId, mvn_repository),
    FOREIGN KEY (provider_id) REFERENCES provider(id)
);
--;
CREATE TABLE IF NOT EXISTS module_set(
    id text primary key,
    name text not null,
    version text not null,
    description text not null,
    provider_id text,
    FOREIGN KEY (provider_id) REFERENCES provider(id),
    UNIQUE (name, version)
);
--;
CREATE TABLE IF NOT EXISTS module_set_element(
    id text primary key,
    module_set_id text not null,
    module_id text not null,
    unique (module_set_id, module_id),
    FOREIGN KEY (module_set_id) REFERENCES module_set(id),
    FOREIGN KEY (module_id) REFERENCES module(id)
);
--;
CREATE TABLE IF NOT EXISTS module(
    id text primary key,
    name text not null,
    version text not null,
    target_platform text not null,
    provider_id text,
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
    unique (name, version, target_platform, provider_id),
    FOREIGN KEY(sha256) REFERENCES artifact(sha256)
);
--;
CREATE TABLE IF NOT EXISTS module_provides(
    id text primary key,
    module_id text not null,
    service text not null,
    "with" text not null,
    unique (module_id, service, "with"),
    FOREIGN KEY(module_id) REFERENCES module(id)
);
--;
CREATE TABLE IF NOT EXISTS module_uses(
    id text primary key,
    module_id text not null,
    service text not null,
    unique (module_id, service),
    FOREIGN KEY(module_id) REFERENCES module(id)
);
--;
CREATE TABLE IF NOT EXISTS module_requires(
    id text primary key,
    module_id text not null,
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
    id text primary key,
    module_id text not null,
    package text not null,
    "to" text,
    mandated boolean not null,
    synthetic boolean not null,
    unique (module_id, package, "to"),
    FOREIGN KEY(module_id) REFERENCES module(id)
);
--;
CREATE TABLE IF NOT EXISTS module_package(
    id text primary key,
    module_id text not null,
    package text not null,
    unique (module_id, package),
    FOREIGN KEY(module_id) REFERENCES module(id)
);
--;
CREATE TABLE IF NOT EXISTS module_hash(
    id text primary key,
    module_id text not null,
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
    id text primary key,
    module_set_id text not null,
    provider_id text not null,
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
    id text primary key,
    provider_id text not null,
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

