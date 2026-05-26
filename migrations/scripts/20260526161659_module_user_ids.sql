-- // module user ids
ALTER TABLE repository.module
ADD COLUMN user_id uuid REFERENCES identity."user"(id)
on update restrict on delete restrict;



-- //@UNDO
ALTER TABLE repository.module
    DROP COLUMN user_id;


