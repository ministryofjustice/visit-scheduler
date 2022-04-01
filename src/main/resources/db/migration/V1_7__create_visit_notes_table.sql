CREATE TABLE visit_notes
(
    id                 serial           NOT NULL PRIMARY KEY,
    visit_id           integer          NOT NULL,
    type               VARCHAR(80)      NOT NULL,
    text               text             NOT NULL
);
