-- placeholder table for testing - will be replaced
CREATE TABLE visit
(
    id              serial        NOT NULL PRIMARY KEY,
    session_template_id  serial,
    reference       VARCHAR(80),
    prisoner_id     VARCHAR(80)   NOT NULL,
    visit_type      VARCHAR(80)   NOT NULL,
    prison_id       VARCHAR(6)    NOT NULL,
    visit_room      VARCHAR(255)  NOT NULL,
    visit_date_time timestamp with time zone NOT NULL,
    visit_status          VARCHAR(80)   NOT NULL,
    active          BOOLEAN       NOT NULL,
    reasonable_adjustments    text
);

CREATE TABLE visit_visitor
(
    visit_id        serial        NOT NULL,
    person_id       serial        NOT NULL,
    lead_visitor    BOOLEAN,
    PRIMARY KEY (visit_id, person_id)
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
    end_time        time          NOT NULL,
    start_date      date          NOT NULL,
    expiry_date     date,
    frequency       VARCHAR(80)
);

CREATE TABLE session_exception
(
    id              serial        NOT NULL PRIMARY KEY,
    session_template_id              serial,
    prison_id       VARCHAR(6)    NOT NULL,
    open_capacity   integer       NOT NULL,
    closed_capacity integer       NOT NULL,
    end_date        date,
    start_date      date          NOT NULL,
    expiry_date     date,
    reason          text
);