-- add visit_id to visit_notification_event table and set it to the visit id
ALTER TABLE visit_notification_event ADD visit_id INTEGER;
UPDATE visit_notification_event vne set visit_id = v.id from visit v where  v.reference = vne.booking_reference ;
ALTER TABLE visit_notification_event ALTER COLUMN visit_id SET NOT NULL;

-- TODO - drop COLUMN after tests
-- ALTER TABLE visit_notification_event DROP COLUMN booking_reference;

ALTER TABLE visit_notification_event
    ADD CONSTRAINT FK_VISIT_NOTIFICATION_EVENT_ON_VISIT FOREIGN KEY (visit_id) REFERENCES visit (id);
