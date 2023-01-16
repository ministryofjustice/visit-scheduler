CREATE INDEX idx_visit_prison_id ON visit(prison_id);
CREATE INDEX idx_session_template_prison_id ON session_template(prison_id);
CREATE INDEX idx_permitted_session_location_prison_id ON permitted_session_location(prison_id);