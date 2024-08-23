-- Fix to update Sudbury (SUI) from old session template to new session template
-- | old session | new session |
-- | ----------- | ----------- |
-- | 	jel.izs.qrp | mjl.fga.ebx |
BEGIN;
SET SCHEMA 'public';

UPDATE session_slot set session_template_reference = 'mjl.fga.ebx' where session_template_reference = 'jel.izs.qrp' and slot_date >= '2024-08-11' and prison_id = (select id from prison where code = 'SUI');

END;
