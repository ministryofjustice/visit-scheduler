CREATE TABLE visit_notification_event
(
    id                  serial          NOT NULL PRIMARY KEY,
    visit_id            integer         NOT NULL,
    type                VARCHAR(80)     NOT NULL,
    create_timestamp    timestamp default current_timestamp
);