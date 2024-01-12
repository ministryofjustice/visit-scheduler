-- Insert session info data from tmp_application and session templates -- time taken 7+ min's
insert into session_slot (session_template_reference,prison_id,slot_date,slot_time,slot_end_time)
select st.reference as session_template_reference,
       v.prison_id,
       v.visit_start::date,
       st.start_time,
       st.end_time
from visit v
         join session_template st on st.reference = v.session_template_reference
group by st.reference,v.visit_start::date,st.start_time,st.end_time,v.prison_id
order by v.visit_start::date;

-- Insert session info data for applications that are migrated -- time taken 6+ min's
insert into session_slot (prison_id,slot_date,slot_time,slot_end_time)
select v.prison_id,v.visit_start::date,v.visit_start::time,v.visit_end::time from visit v
where v.session_template_reference is null
group by v.visit_start::date,v.visit_start::time,v.visit_end::time,prison_id order by v.visit_start::date;

-- Update slot to have references -- time taken 1+ min
update session_slot SET reference = REGEXP_REPLACE(to_hex(id+2951597050), '(.{3})(?!$)', '\1~','g')
where reference is null;

-- Now that all reference have been updated we get changed the colum for slot reference to be compulsory.
ALTER TABLE session_slot ALTER COLUMN reference SET NOT NULL;

-- Create indexes -- time taken 1-min's
CREATE INDEX idx_session_slot_session_template_reference ON session_slot(session_template_reference);
CREATE INDEX idx_session_index ON session_slot(prison_id,slot_date,slot_time,slot_end_time);


