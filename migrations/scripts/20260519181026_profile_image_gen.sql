-- // insert trigger
CREATE OR REPLACE FUNCTION identity_user_genProfileImage_insert_function()
    RETURNS TRIGGER AS
$$
BEGIN
    INSERT INTO proletarian.job(job_type, payload)
    VALUES (':identity.user/genProfileImage', row_to_json(NEW));
    return NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER identity_user_genProfileImage_insert_trigger
    AFTER INSERT
    ON identity.user
    FOR EACH ROW
EXECUTE PROCEDURE identity_user_genProfileImage_insert_function();

-- //@UNDO
DROP TRIGGER identity_user_genProfileImage_insert_trigger ON identity.user;
DROP FUNCTION identity_user_genProfileImage_insert_function;

