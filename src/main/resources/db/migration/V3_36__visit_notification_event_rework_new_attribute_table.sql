-- V3_36__visit_notification_event_rework_new_attribute_table.sql

-- Step 1: Create the new table visit_notification_event_attribute
CREATE TABLE visit_notification_event_attribute (
    id                          SERIAL                   NOT NULL PRIMARY KEY,
    visit_notification_event_id INTEGER                  NOT NULL,
    attribute_name              VARCHAR(80)              NOT NULL,
    attribute_value             TEXT                     NOT NULL,
    CONSTRAINT fk_visit_notification_event FOREIGN KEY (visit_notification_event_id)
    REFERENCES visit_notification_event (id) ON DELETE CASCADE
);

-- Step 2: Remove columns from visit_notification_event
ALTER TABLE visit_notification_event
    DROP COLUMN description,
    DROP COLUMN visitor_id,
    DROP COLUMN visitor_restriction_type;