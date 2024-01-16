CREATE TABLE session_slot_no_order
(
    id                      	serial          NOT NULL PRIMARY KEY,
    session_template_reference 	text,
    prison_id                   integer         NOT NULL,
    slot_date              		date            NOT NULL,
    slot_time              		time            NOT NULL,
    slot_end_time               time            NOT NULL
);

-- Insert session info data for applications that are migrated -- time taken 6+ min's
INSERT INTO session_slot_no_order (prison_id,slot_date,slot_time,slot_end_time)
    SELECT v.prison_id,v.visit_start::date,v.visit_start::time,v.visit_end::time FROM visit v
        WHERE v.session_template_reference IS NULL
        GROUP BY v.visit_start::DATE,v.visit_start::TIME,v.visit_end::TIME,prison_id ORDER BY v.visit_start::DATE;

-- Insert session info data from tmp_application and session templates -- time taken 7+ min's
INSERT INTO session_slot_no_order (session_template_reference,prison_id,slot_date,slot_time,slot_end_time)
    SELECT st.reference AS session_template_reference,
           v.prison_id,
           v.visit_start::date,
           st.start_time,
           st.end_time
        FROM visit v
                 JOIN session_template st ON st.reference = v.session_template_reference
        GROUP BY st.reference,v.visit_start::DATE,st.start_time,st.end_time,v.prison_id
        ORDER BY v.visit_start::DATE;

INSERT INTO session_slot (reference,session_template_reference,prison_id,slot_date,slot_time,slot_end_time)
        SELECT REGEXP_REPLACE(to_hex(id+2951597050), '(.{3})(?!$)', '\1~','g'),session_template_reference,prison_id,slot_date,slot_time,slot_end_time FROM session_slot_no_order ORDER BY slot_date,slot_time;

TRUNCATE TABLE session_slot_no_order;
DROP TABLE session_slot_no_order;

-- Create indexes -- time taken 1-min's
CREATE INDEX idx_session_slot_session_template_reference ON session_slot(session_template_reference);
CREATE INDEX idx_session_index ON session_slot(prison_id,slot_date,slot_time,slot_end_time);




