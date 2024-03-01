-- Searches by prisoner id were very slow so we have added this index
CREATE INDEX idx_visit_prisoner_id ON visit(prisoner_id);

