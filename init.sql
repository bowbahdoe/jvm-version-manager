CREATE TABLE IF NOT EXISTS artifact(
    sha256 text primary key,
    data blob not null,
    unique (sha256)
);
--;
CREATE TABLE IF NOT EXISTS provider(
    id integer primary key,
    name text not null unique
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
    description text not null,
    provider_id integer not null,
    FOREIGN KEY (provider_id) REFERENCES provider(id)
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
    FOREIGN KEY(sha256) REFERENCES artifact(id)
);
--;
CREATE TABLE IF NOT EXISTS module_provides(
    id integer primary key,
    module_id integer not null,
    service text not null,
    with text not null,
    unique (module_id, service, with),
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
