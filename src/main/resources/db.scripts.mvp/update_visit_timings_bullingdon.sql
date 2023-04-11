-- Temporary fix to update HMP Bullingdon visit timings to start at 13:45
-- this will need to be updated via the csv file post implementation of VB-2090

BEGIN;
    SET SCHEMA 'public';

    UPDATE session_template st
        SET start_time = '13:45', end_time = '15:45'
    FROM prison p
    WHERE st.prison_id = p.id
        AND st.start_time = '14:15'
        AND st.end_time  = '15:45'
        AND p.code = 'BNI';

    UPDATE visit v set visit_start = visit_start - interval '30 minutes'
    FROM prison p
    WHERE v.prison_id = p.id
      AND (v.visit_start::time = '14:15:00')
      AND (v.visit_end::time = '15:45:00')
      AND p.code = 'BNI'
      AND 	v.visit_start >= TO_DATE('11/04/2023', 'DD/MM/YYYY')
      AND v.visit_status = 'BOOKED';
END;
