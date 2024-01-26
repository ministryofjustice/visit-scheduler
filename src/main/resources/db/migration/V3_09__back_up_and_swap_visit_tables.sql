ALTER TABLE visit RENAME TO visit_backup;
ALTER TABLE tmp_visit RENAME TO visit;

-- now add this constraint to visit table..
ALTER TABLE visits_to_applications
    ADD CONSTRAINT visit_must_exist
        FOREIGN KEY (visit_id)
            REFERENCES visit(id);
