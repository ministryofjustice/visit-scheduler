CREATE TABLE visits_to_applications (
       visit_id integer NOT NULL,
       application_id integer NOT NULL,
       PRIMARY KEY (visit_id, application_id),
       CONSTRAINT visit_must_exist           FOREIGN KEY (visit_id) REFERENCES visit(id),
       CONSTRAINT application_must_exist     FOREIGN KEY (application_id) REFERENCES application(id)
);

insert into visits_to_applications
        SELECT  v.id, a.id FROM tmp_visit v
            JOIN application a on a.booking_reference = v.reference
            WHERE ((v.visit_status  = 'BOOKED') or (v.visit_status  = 'CANCELLED' and v.outcome_status != 'SUPERSEDED_CANCELLATION'));
