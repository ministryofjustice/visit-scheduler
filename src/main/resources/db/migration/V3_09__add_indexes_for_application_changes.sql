ALTER TABLE visit ADD CONSTRAINT visit_id_pk PRIMARY KEY (id);
CREATE INDEX idx_visit_prison        ON visit(prison_id);
CREATE INDEX idx_reference_for_visit ON visit(reference);
