-- Insert session info data from tmp_application and session templates -- time taken 7+ min's
INSERT INTO session_slot (session_template_reference,prison_id,slot_date,slot_time,slot_end_time)
    SELECT st.reference AS session_template_reference,
           v.prison_id,
           v.visit_start::date,
           st.start_time,
           st.end_time
        FROM visit v
                 JOIN session_template st ON st.reference = v.session_template_reference
        GROUP BY st.reference,v.visit_start::DATE,st.start_time,st.end_time,v.prison_id
        ORDER BY v.visit_start::DATE;

-- Insert session info data for applications that are migrated -- time taken 6+ min's
INSERT INTO session_slot (prison_id,slot_date,slot_time,slot_end_time)
    SELECT v.prison_id,v.visit_start::date,v.visit_start::time,v.visit_end::time FROM visit v
        WHERE v.session_template_reference IS NULL
    GROUP BY v.visit_start::DATE,v.visit_start::TIME,v.visit_end::TIME,prison_id ORDER BY v.visit_start::DATE;

-- Update slot to have references -- time taken 1+ min
UPDATE session_slot SET reference = REGEXP_REPLACE(to_hex(id+2951597050), '(.{3})(?!$)', '\1~','g')
    WHERE reference IS NULL;

-- Now that all reference have been updated we get changed the colum for slot reference to be compulsory.
ALTER TABLE session_slot ALTER COLUMN reference SET NOT NULL;

-- Create indexes -- time taken 1-min's
CREATE INDEX idx_session_slot_session_template_reference ON session_slot(session_template_reference);
CREATE INDEX idx_session_index ON session_slot(prison_id,slot_date,slot_time,slot_end_time);


