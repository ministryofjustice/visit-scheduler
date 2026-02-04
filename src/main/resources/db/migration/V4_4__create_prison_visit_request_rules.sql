CREATE TABLE prison_visit_request_rules
(
    id                      SERIAL          PRIMARY KEY,
    prison_id               integer         NOT NULL,
    rule_name               varchar(40)     NOT NULL,
    active                  boolean         NOT NULL,

    CONSTRAINT prison_rules_prison  FOREIGN KEY (prison_id) REFERENCES prison(id)
);
