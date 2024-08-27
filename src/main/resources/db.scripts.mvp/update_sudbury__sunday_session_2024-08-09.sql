-- Fix to update Sudbury (SUI) from old session template to new session template
-- | old session | new session |
-- | ----------- | ----------- |
-- | gel.ina.ree | qjl.fne.slr |
BEGIN;
SET SCHEMA 'public';

UPDATE session_slot set session_template_reference = 'qjl.fne.slr' where session_template_reference = 'gel.ina.ree' and slot_date >= '2024-08-12' and prison_id = (select id from prison where code = 'SUI');

END;
