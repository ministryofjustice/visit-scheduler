-- An SQL script was merged to update a session_template_reference but it didn't update the session_slot start & end time.
-- This script finds all visits which have the wrong start and end time and moves them to the correct time.

BEGIN;
SET SCHEMA 'public';

update session_slot
set
    slot_start = slot_start::date + time '13:30:00' at time zone 'Europe/London',
    slot_end = slot_end::date + time '14:30:00' at time zone 'Europe/London'
where (session_template_reference = 'mjl.fga.ebx' OR session_template_reference = 'qjl.fne.slr')
  and slot_start::time = '13:15:00'
  and slot_end::time = '14:15:00'

END;
