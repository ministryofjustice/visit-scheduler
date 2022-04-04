CREATE TABLE visit_support
(
    id                      serial          NOT NULL PRIMARY KEY,
    visit_id                integer         NOT NULL,
    support_name            VARCHAR(80)     NOT NULL,
    support_details text
);

CREATE INDEX idx_visit_support_visit_id ON visit_support(visit_id);