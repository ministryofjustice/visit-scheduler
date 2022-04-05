CREATE TABLE visit
(
    id                      serial          NOT NULL PRIMARY KEY,
    reference               text            UNIQUE,
    prisoner_id             VARCHAR(80)     NOT NULL,
    visit_type              VARCHAR(80)     NOT NULL,
    prison_id               VARCHAR(6)      NOT NULL,
    visit_room              VARCHAR(255)    NOT NULL,
    visit_start             timestamp with time zone NOT NULL,
    visit_end               timestamp with time zone NOT NULL,
    visit_status            VARCHAR(80)     NOT NULL,
    visit_restriction       VARCHAR(80)     NOT NULL,
    create_timestamp        timestamp default current_timestamp,
    modify_timestamp        timestamp default current_timestamp
);
