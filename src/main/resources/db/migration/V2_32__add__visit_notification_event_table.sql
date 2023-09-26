CREATE TABLE visit_notification_event
(
    id                  serial          NOT NULL PRIMARY KEY,
    booking_reference   text            NOT NULL,
    type                VARCHAR(80)     NOT NULL,
    create_timestamp    timestamp default current_timestamp
);