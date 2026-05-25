-- // add_qa_site
CREATE SCHEMA identity;


CREATE TABLE identity."user"
(
    id                       uuid        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    profile_image_png_base64 text,
    github_user_id           text unique,
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
VALUES ('java'), ('spring'), ('code review'), ('other');


-- //@UNDO
DROP TABLE qa.question_tag;
DROP TABLE qa.tag;
DROP TABLE qa.question;

DROP SCHEMA qa;

DROP TABLE identity.organization_user;
DROP TABLE identity.organization;
DROP TABLE identity.user;


DROP SCHEMA identity;


