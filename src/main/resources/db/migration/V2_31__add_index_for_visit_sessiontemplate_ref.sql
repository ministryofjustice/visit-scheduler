CREATE INDEX idx_visit_to_session_template ON visit(session_template_reference);
CREATE INDEX idx_session_template_start_time ON session_template(start_time);
CREATE INDEX idx_session_template_end_time ON session_template(end_time);
