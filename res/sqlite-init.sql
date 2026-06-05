CREATE TABLE IF NOT EXISTS module
(
    id              text primary key,
    name            text    not null,
    version         text,
    target_platform text,
    open            integer not null check (open in (0, 1)),
    mandated        integer not null check (mandated in (0, 1)),
    synthetic       integer not null check (synthetic in (0, 1)),
    cid             text    not null unique
) STRICT;
--;
CREATE TABLE IF NOT EXISTS module_provides
(
    id        text primary key,
    module_id text not null,
    service   text not null,
    "with"    text not null,
    unique (module_id, service, "with"),
    FOREIGN KEY (module_id) REFERENCES module (id)
) STRICT;
--;
CREATE TABLE IF NOT EXISTS module_uses
(
    id        text primary key,
    module_id text not null,
    service   text not null,
    unique (module_id, service),
    FOREIGN KEY (module_id) REFERENCES module (id)
) STRICT;
--;
CREATE TABLE IF NOT EXISTS module_requires
(
    id         text primary key,
    module_id  text    not null,
    module     text    not null,
    version    text,
    static     integer not null check ( static in (0, 1) ),
    transitive integer not null check ( transitive in (0, 1) ),
    mandated   integer not null check ( mandated in (0, 1) ),
    synthetic  integer not null check ( synthetic in (0, 1) ),
    unique (module_id, module),
    FOREIGN KEY (module_id) REFERENCES module (id)
) STRICT;
--;
CREATE TABLE IF NOT EXISTS module_exports
(
    id        text primary key,
    module_id text    not null,
    package   text    not null,
    mandated  integer not null check ( mandated in (0, 1) ),
    synthetic integer not null check ( synthetic in (0, 1) ),
    unique (module_id, package),
    FOREIGN KEY (module_id) REFERENCES module (id)
) STRICT;
--;
CREATE TABLE IF NOT EXISTS module_exports_to
(
    id                text primary key,
    module_exports_id text not null,
    module            text not null,
    unique (module_exports_id, module),
    FOREIGN KEY (module_exports_id) REFERENCES module_exports (id)
) STRICT;
--;
CREATE TABLE IF NOT EXISTS module_package
(
    id        text primary key,
    module_id text not null,
    package   text not null,
    unique (module_id, package),
    FOREIGN KEY (module_id) REFERENCES module (id)
) STRICT;
--;
CREATE TABLE IF NOT EXISTS module_hash
(
    id        text primary key,
    module_id text not null,
    module    text not null,
    algorithm text not null,
    hash      text not null,
    unique (module_id, module, algorithm),
    FOREIGN KEY (module_id) REFERENCES module (id)
) STRICT;
--;
-- A module published for the consumption of
-- other users.
CREATE TABLE IF NOT EXISTS published_module
(
    id          text primary key,
    module_id   text not null,
    atproto_did text not null,
    unique (module_id, atproto_did)
) STRICT;
--;

