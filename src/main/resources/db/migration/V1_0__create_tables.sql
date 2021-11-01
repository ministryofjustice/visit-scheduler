-- placeholder table for testing - will be replaced
CREATE TABLE visit
(
    id              serial      NOT NULL PRIMARY KEY,
    prisoner_id     VARCHAR(80) NOT NULL,
    visit_date_time timestamp with time zone,
    active          BOOLEAN     NOT NULL
);

CREATE TABLE session_template
(
    id              serial        NOT NULL PRIMARY KEY,
    prison_id       VARCHAR(6)    NOT NULL,
    restrictions    text,
    visit_room      VARCHAR(255)  NOT NULL,
    visit_type      VARCHAR(80)   NOT NULL,
    open_capacity   integer       NOT NULL,
    closed_capacity integer       NOT NULL,
    start_time      time          NOT NULL,
    end_time     time          NOT NULL,
    start_date      date          NOT NULL,
    expiry_date     date,
    frequency       VARCHAR(80)
);

CREATE TABLE visitor
(
    id              serial      NOT NULL PRIMARY KEY,
    person_id       BIGINT    NOT NULL,
    visit_date_time timestamp with time zone,
    active          BOOLEAN     NOT NULL
);
