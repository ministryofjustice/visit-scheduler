CREATE TABLE booking
(
    id                      serial          NOT NULL PRIMARY KEY,
    reservation_id          integer          NOT NULL,
    prisoner_id             VARCHAR(80)     NOT NULL,
    visit_type              VARCHAR(80)     NOT NULL,
    prison_id               VARCHAR(6)      NOT NULL,
    visit_status            VARCHAR(80)     NOT NULL,
    outcome_status          VARCHAR(40)
);
