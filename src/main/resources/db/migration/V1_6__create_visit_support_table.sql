CREATE TABLE visit_support
(
    id                      serial          NOT NULL PRIMARY KEY,
    visit_id                integer         NOT NULL,
    support_name            VARCHAR(80)     NOT NULL,
    support_details text
);
