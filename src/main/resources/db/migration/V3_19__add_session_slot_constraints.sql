-- UNIQUE constraint for session slot
ALTER TABLE session_slot ADD UNIQUE NULLS NOT DISTINCT (session_template_reference,prison_id,slot_start,slot_end);