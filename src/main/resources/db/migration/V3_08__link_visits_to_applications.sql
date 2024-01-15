insert into visits_to_applications
        SELECT  v.id, a.id FROM tmp_visit v
            JOIN application a on a.booking_reference = v.reference
            WHERE ((v.visit_status  = 'BOOKED') or (v.visit_status  = 'CANCELLED' and v.outcome_status != 'SUPERSEDED_CANCELLATION'));

ALTER TABLE visits_to_applications ADD CONSTRAINT visits_to_applications_pl PRIMARY KEY (visit_id, application_id);

ALTER TABLE visits_to_applications
    ADD CONSTRAINT visit_must_exist
        FOREIGN KEY (visit_id)
            REFERENCES visit(id);

ALTER TABLE visits_to_applications
    ADD CONSTRAINT application_must_exist
        FOREIGN KEY (application_id)
            REFERENCES application(id);

-- now that the link is done we can remove booking reference from application
ALTER TABLE application DROP booking_reference;