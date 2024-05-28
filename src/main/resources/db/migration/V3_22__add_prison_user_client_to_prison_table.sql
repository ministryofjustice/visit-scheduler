CREATE TABLE prison_user_client
(
    id                      serial          NOT NULL PRIMARY KEY,
    prison_id               integer         NOT NULL,
    user_type               VARCHAR(80)     NOT NULL,
    active                  boolean         NOT NULL DEFAULT false,
    create_timestamp        timestamp NULL DEFAULT CURRENT_TIMESTAMP,
    modify_timestamp        timestamp NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO prison_user_client (prison_id,user_type, active) SELECT id,'STAFF',active FROM prison;