-- // discord user id
ALTER TABLE identity."user" ADD COLUMN discord_user_id text unique;



-- //@UNDO

ALTER TABLE identity."user" DROP COLUMN IF EXISTS discord_user_id;


