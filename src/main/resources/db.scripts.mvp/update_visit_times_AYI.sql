
-- This sql makes sure visits align to the correct session times and session templates. See : https://dsdmoj.atlassian.net/browse/VB-3121

UPDATE visit
SET visit_start = date_trunc('day', tmp.visit_start) + '14:00:00',
    visit_end = date_trunc('day', tmp.visit_end) + '16:00:00'
    FROM (select v.* from visit v
  join prison p on p.id = v.prison_id 
  join session_template st on st.reference = v.session_template_reference
  join legacy_data ld on ld.visit_id = v.id
  where p.code = 'AYI' and v.visit_start::time = '14:30' and v.visit_end::time = '16:30') AS tmp
WHERE tmp.id = visit.id;