CREATE INDEX idx_event_audit_booked_visit ON event_audit (booking_reference, actioned_by_id) WHERE type = 'BOOKED_VISIT';

CREATE INDEX idx_event_audit_reserved_visit ON event_audit (booking_reference, actioned_by_id) WHERE type = 'RESERVED_VISIT';

CREATE INDEX idx_visit_user_status_slot ON visit (user_type, visit_status, session_slot_id);
