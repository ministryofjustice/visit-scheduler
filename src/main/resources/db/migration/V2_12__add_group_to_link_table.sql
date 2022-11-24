ALTER TABLE session_to_permitted_location ADD location_group VARCHAR(80) NOT NULL;
CREATE INDEX idx_permitted_location_group_id ON session_to_permitted_location(location_group);