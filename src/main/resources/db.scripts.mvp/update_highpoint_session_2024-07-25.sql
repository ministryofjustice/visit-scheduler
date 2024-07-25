-- Fix to update Highpoint (HPI) from old session template to new session template
-- | old session | new session |
-- | ----------- | ----------- |
-- | gdl.czr.ylp | qgl.csq.aqb |
BEGIN;
SET SCHEMA 'public';

UPDATE session_slot set session_template_reference = 'qgl.csq.aqb' where session_template_reference = 'gdl.czr.ylp' and slot_date >= '2024-07-25' and prison_id = (select id from prison where code = 'HPI');

END;
