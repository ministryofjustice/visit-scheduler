ALTER TABLE tmp_visit ADD CONSTRAINT visit_id_pk PRIMARY KEY (id);
CREATE INDEX idx_visit_prison        ON tmp_visit(prison_id);
CREATE INDEX idx_reference_for_visit ON tmp_visit(reference);
