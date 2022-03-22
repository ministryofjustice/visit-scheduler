CREATE TABLE visit
(
    id                      text            NOT NULL PRIMARY KEY,
    session_template_id     integer,
    prisoner_id             VARCHAR(80)     NOT NULL,
    visit_type              VARCHAR(80)     NOT NULL,
    prison_id               VARCHAR(6)      NOT NULL,
    visit_room              VARCHAR(255)    NOT NULL,
    visit_start             timestamp with time zone NOT NULL,
    visit_end               timestamp with time zone NOT NULL,
    status                  VARCHAR(80)     NOT NULL,
    visitor_concerns        text,
    create_timestamp        timestamp default current_timestamp,
    modify_timestamp        timestamp default current_timestamp
);

CREATE SEQUENCE visit_seq OWNED BY visit.id;