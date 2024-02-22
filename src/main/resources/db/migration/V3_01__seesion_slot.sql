-- Insert session info data for applications that are migrated
INSERT INTO session_slot_no_order (prison_id,slot_date,slot_start,slot_end)
    SELECT v.prison_id,v.visit_start::date,v.visit_start,v.visit_end FROM visit v
        WHERE v.session_template_reference IS NULL
        GROUP BY v.visit_start::DATE,v.visit_start,v.visit_end,prison_id ORDER BY v.visit_start;

-- Insert session info data from tmp_application and session templates
INSERT INTO session_slot_no_order (session_template_reference,prison_id,slot_date,slot_start,slot_end)
        SELECT st.reference AS session_template_reference,
               v.prison_id,
               v.visit_start::date,
                v.visit_start::date + st.start_time,
                v.visit_end::date + st.end_time
        FROM visit v
                 JOIN session_template st ON st.reference = v.session_template_reference
        GROUP BY
            st.reference,
            v.prison_id,
            v.visit_start::date,
            v.visit_end::date,
            st.start_time,
            st.end_time
        ORDER BY v.visit_start::DATE;

INSERT INTO session_slot (reference,session_template_reference,prison_id,slot_date,slot_start,slot_end)
        SELECT REGEXP_REPLACE(to_hex(id+2951597050), '(.{3})(?!$)', '\1~','g'),session_template_reference,prison_id,slot_date,slot_start,slot_end
                FROM session_slot_no_order ORDER BY slot_start,prison_id,session_template_reference;

TRUNCATE TABLE session_slot_no_order;
DROP TABLE session_slot_no_order;

-- Create indexes
CREATE INDEX idx_session_slot_session_template_reference ON session_slot(session_template_reference);
CREATE INDEX idx_session_index ON session_slot(prison_id,slot_date,slot_start,slot_end);




