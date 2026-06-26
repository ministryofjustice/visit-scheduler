CREATE INDEX idx_application_prisoner_id ON application(prisoner_id);

-- Speeds up active reservation capacity counts by session slot and restriction.
-- This is one of the hottest application-table read paths and the partial index
-- avoids indexing retained ACCEPTED application history.
CREATE INDEX IF NOT EXISTS idx_application_active_slot_restriction
    ON application (session_slot_id, restriction, modify_timestamp)
    WHERE application_status = 'IN_PROGRESS'
        AND reserved_slot = true;

-- Speeds up checks for whether a prisoner already has an active reservation on a
-- session slot. This supports double-booking/reservation filtering.
CREATE INDEX IF NOT EXISTS idx_application_active_prisoner_slot
    ON application (prisoner_id, session_slot_id, modify_timestamp)
    WHERE application_status = 'IN_PROGRESS'
        AND reserved_slot = true;

-- Speeds up the expired application cleanup task, which only cares about old
-- IN_PROGRESS applications ordered by id.
CREATE INDEX IF NOT EXISTS idx_application_in_progress_expiry
    ON application (modify_timestamp, id)
    WHERE application_status = 'IN_PROGRESS';

-- Speeds up updating RESERVED_VISIT/CHANGING_VISIT audit rows when an application
-- becomes a booking. Existing event_audit(application_reference) already helps,
-- but this partial index is smaller and targeted.
CREATE INDEX IF NOT EXISTS idx_event_audit_application_reserved_changing
    ON event_audit (application_reference)
    WHERE type IN ('RESERVED_VISIT', 'CHANGING_VISIT');

-- Supports joins from event_audit to actioned_by. Useful if public/history queries
-- by booker are slow, but this adds write cost to every event_audit insert.
CREATE INDEX IF NOT EXISTS idx_event_audit_actioned_by_id
    ON event_audit (actioned_by_id);

-- Supports joins from visit_notification_event back to visit. PostgreSQL does not
-- automatically index foreign key columns.
CREATE INDEX IF NOT EXISTS idx_visit_notification_event_visit_id
    ON visit_notification_event (visit_id);

-- Supports joins from visit_notification_event_attribute back to its parent event.
-- PostgreSQL does not automatically index foreign key columns.
CREATE INDEX IF NOT EXISTS idx_visit_notification_event_attribute_event_id
    ON visit_notification_event_attribute (visit_notification_event_id);
