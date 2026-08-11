-- UNIQUE constraint for prison_user_client
ALTER TABLE prison_user_client ADD CONSTRAINT prison_user_client_unq_prison_user_type UNIQUE (prison_id, user_type);
