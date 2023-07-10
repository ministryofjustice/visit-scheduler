CREATE TABLE event_audit
(
    id                          serial          NOT NULL PRIMARY KEY,
    booking_reference           text,
    application_reference       text,
    session_template_reference  text,
    type                        varchar(90) NOT NULL,
    application_method_type     varchar(90) NOT NULL,
    actioned_by                 varchar(60),
    create_timestamp            timestamp default current_timestamp
);

CREATE INDEX idx_booking_reference ON event_audit(booking_reference);
CREATE INDEX idx_visit_application_reference ON event_audit(application_reference);
CREATE INDEX idx_visit_session_template_reference ON event_audit(session_template_reference);

-- Copy data over from session_template table
INSERT INTO event_audit (booking_reference, application_reference, session_template_reference, type, application_method_type, actioned_by, create_timestamp)
    (SELECT reference,
            application_reference,
            session_template_reference,
            CASE
                WHEN visit_status   = 'RESERVED' THEN 'RESERVED_VISIT'
                WHEN visit_status   = 'CHANGING' THEN 'CHANGING_VISIT'
                WHEN visit_status   = 'BOOKED' THEN 'BOOKED_VISIT'
                WHEN outcome_status = 'SUPERSEDED_CANCELLATION' THEN 'UPDATED_VISIT'
                WHEN visit_status   = 'CANCELLED' THEN 'CANCELED_VISIT'
                END as type,
            'NOT_KNOWN' AS application_method_type,
            CASE
                WHEN visit_status   = 'CANCELLED' THEN cancelled_by
                WHEN outcome_status = 'SUPERSEDED_CANCELLATION' THEN updated_by
                WHEN updated_by is not null THEN updated_by
                ELSE created_by
                END AS actioned_by,
            CASE
                WHEN modify_timestamp is null THEN create_timestamp
                ELSE modify_timestamp
                END AS create_timestamp
     FROM visit
      ORDER BY create_timestamp DESC);

UPDATE event_audit	SET type = 'MIGRATED_VISIT'
    FROM (select reference  from visit v join legacy_data ld on ld.visit_id  = v.id WHERE v.visit_status  = 'BOOKED') AS v
WHERE event_audit.booking_reference = v.reference;

ALTER TABLE visit DROP created_by;
ALTER TABLE visit DROP updated_by;
ALTER TABLE visit DROP cancelled_by;