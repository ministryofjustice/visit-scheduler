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

CREATE TEMP TABLE tmp_updated_visits(visit_id int not null);

INSERT INTO tmp_updated_visits(visit_id)
    select v.id from (
                         select reference  from visit where (visit_status = 'BOOKED' or outcome_status='SUPERSEDED_CANCELLATION')
                         group by reference  having count(reference) > 1) as tmp
                         join visit v on v.reference = tmp.reference
    where visit_status = 'BOOKED'
    order by v.reference,v.id;

-- Copy data over from session_template table
INSERT INTO event_audit (booking_reference, application_reference, session_template_reference, type, application_method_type, actioned_by, create_timestamp)
    (SELECT
            reference,
            application_reference,
            session_template_reference,
     CASE
         WHEN visit_status   = 'RESERVED' THEN 'RESERVED_VISIT'
         WHEN visit_status   = 'CHANGING' THEN 'CHANGING_VISIT'
         WHEN outcome_status = 'SUPERSEDED_CANCELLATION'  THEN 'BOOKED_VISIT'
         WHEN visit_status  = 'CANCELLED' THEN 'CANCELLED_VISIT'
         WHEN id in (select * from tmp_updated_visits) THEN 'UPDATED_VISIT'
        else  'BOOKED_VISIT'
    END as type,
    'NOT_KNOWN' AS application_method_type,
    case
        WHEN visit_status   = 'RESERVED' THEN created_by
        WHEN visit_status   = 'CHANGING' THEN created_by
        WHEN outcome_status = 'SUPERSEDED_CANCELLATION' THEN created_by
        WHEN visit_status  = 'CANCELLED' THEN cancelled_by
        WHEN id in (select * from tmp_updated_visits) THEN updated_by
        else  created_by
    END AS actioned_by,
    CASE
        WHEN modify_timestamp is null THEN create_timestamp
        ELSE modify_timestamp
    END AS create_timestamp
    FROM visit v where created_by is not null
    ORDER BY reference desc);

-- use application Id because this will be unique for first migrated visit
UPDATE event_audit	SET type = 'MIGRATED_VISIT'
    FROM (select application_reference  from visit v join legacy_data ld on ld.visit_id  = v.id WHERE v.visit_status  = 'BOOKED') AS v
WHERE event_audit.application_reference = v.application_reference AND type = 'BOOKED_VISIT';

-- need to delete at later stage... but leaving for now to allow us to check data
-- ALTER TABLE visit DROP created_by;
-- ALTER TABLE visit DROP updated_by;
-- ALTER TABLE visit DROP cancelled_by;


