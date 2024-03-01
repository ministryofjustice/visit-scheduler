CREATE INDEX idx_session_slot_date_index ON session_slot(slot_date);
CREATE INDEX idx_session_slot_start_index ON session_slot(slot_start);
CREATE INDEX idx_session_slot_end_index ON session_slot(slot_end);

CREATE INDEX idx_application_restriction_index ON application(restriction);
CREATE INDEX idx_application_reserved_index ON application(reserved_slot);
CREATE INDEX idx_application_completed_index ON application(completed);

CREATE INDEX idx_visit_restriction_index ON visit(visit_restriction);
CREATE INDEX idx_visit_status_index ON visit(visit_status);