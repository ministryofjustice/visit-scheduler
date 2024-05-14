BEGIN;

    --  Update prison BFI migrated visits to correct start and end time, remove ss.session_template_reference in clause to make it generic

    SET SCHEMA 'public';

    UPDATE session_slot
    SET slot_start = date_trunc('day', tmp.slot_date) + tmp.correct_start_time, slot_end = date_trunc('day', tmp.slot_date) + tmp.corect_end_time
        FROM (

            SELECT ss.id AS slot_id, st.start_time AS correct_start_time, st.end_time AS corect_end_time, ss.slot_date
                FROM session_slot ss
                LEFT JOIN session_template st ON st.reference = ss.session_template_reference
                WHERE (ss.slot_start::time!=st.start_time OR ss.slot_end::time!=st.end_time) AND ss.slot_date >= current_date
                    AND ss.session_template_reference IN ('krl.fop.pwo','zbl.cxz.axn','abl.cgz.nov','lbl.fxe.xlw')
            ) AS tmp

    WHERE tmp.slot_id = session_slot.id;


END;