-- This is an update for a use case we missed in migration for cancelled migrations
UPDATE event_audit	SET type = 'MIGRATED_VISIT'
    FROM (select application_reference  from visit v join legacy_data ld on ld.visit_id  = v.id WHERE v.visit_status  = 'CANCELLED') AS v
WHERE event_audit.application_reference = v.application_reference AND type = 'CANCELLED_VISIT';

