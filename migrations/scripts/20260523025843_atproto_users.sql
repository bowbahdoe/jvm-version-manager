-- // atproto users
DELETE FROM identity.user;
ALTER TABLE identity."user"
    ADD COLUMN atproto_did text not null unique



-- //@UNDO
ALTER TABLE identity."user"
    DROP COLUMN atproto_did;


