-- // link github account
CREATE SCHEMA github;

CREATE TABLE github.linked_account
(
    id                              uuid primary key     default uuidv7(),
    created_at                      timestamptz not null default now(),
    updated_at                      timestamptz not null default now(),
    user_id                         uuid        not null references identity.user (id)
        on update restrict
        on delete restrict,
    github_user_id                  text        not null unique,
    github_username                 text        not null,
    github_profile_image_png_base64 text
);

ALTER TABLE identity."user"
    DROP COLUMN github_user_id;

-- //@UNDO
DROP SCHEMA discord;



