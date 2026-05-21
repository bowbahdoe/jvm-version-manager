-- // provider orgs
ALTER TABLE repository.provider
    ADD COLUMN organization_id uuid;

ALTER TABLE repository.provider
    ADD CONSTRAINT provider_organization_fk
        FOREIGN KEY (organization_id)
            REFERENCES identity.organization (id)
            ON UPDATE RESTRICT
            ON DELETE RESTRICT;


-- //@UNDO
-- SQL to undo the change goes here.

ALTER TABLE repository.provider
    DROP CONSTRAINT provider_organization_fk;

ALTER TABLE repository.provider
    DROP COLUMN organization_id;