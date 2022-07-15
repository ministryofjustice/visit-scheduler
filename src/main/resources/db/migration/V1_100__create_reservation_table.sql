CREATE TABLE reservation
(
    id                      serial          NOT NULL PRIMARY KEY,
    reference               text            UNIQUE,
    visit_room              VARCHAR(255)    NOT NULL,
    visit_start             timestamp with time zone NOT NULL,
    visit_end               timestamp with time zone NOT NULL,
    visit_restriction       VARCHAR(80)     NOT NULL,
    create_timestamp        timestamp default current_timestamp,
    modify_timestamp        timestamp default current_timestamp
);
