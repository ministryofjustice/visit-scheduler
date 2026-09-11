CREATE TABLE prison_visit_request_rules_config (
    id                                      SERIAL          PRIMARY KEY,
    prison_visit_request_rule_id            INTEGER         NOT NULL,
    attribute_name                          VARCHAR(80)     NOT NULL,
    attribute_value                         TEXT            NOT NULL,

    CONSTRAINT prison_visit_request_rules_config_prison_visit_request_rules  FOREIGN KEY (prison_visit_request_rule_id) REFERENCES prison_visit_request_rules(id)
);
