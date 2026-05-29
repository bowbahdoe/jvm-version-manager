-- // link discord
CREATE SCHEMA discord;

CREATE TABLE discord.linked_account
(
    id                               uuid primary key     default uuidv7(),
    created_at                       timestamptz not null default now(),
    updated_at                       timestamptz not null default now(),
    user_id                          uuid        not null references identity.user (id)
        on update restrict
        on delete restrict,
    discord_user_id                  text        not null unique,
    discord_username                 text        not null,
    discord_profile_image_png_base64 text        not null
);

-- //@UNDO

DROP TABLE discord.linked_account;
DROP SCHEMA discord;
