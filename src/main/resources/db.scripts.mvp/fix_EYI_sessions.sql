-- Fix to update Elmley (EYI) from old session template to new session template
-- | new session | old session | day       |
-- | ----------- | ----------- | --------- |
-- | yyy.ieb.jnz | yzy.cja.bow | Tuesday   |
-- | xyy.urm.bdw | xzy.ulq.pjv | Wednesday |
-- | myy.fwz.qsj | mzy.uyr.rew | Thursday  |
-- | qyy.cdj.ryo | qzy.cag.sgy | Friday    |
-- | ryy.ior.zvd | rzy.ixl.las | Saturday  |
-- | byy.iqw.azq | bzy.inj.nsd | Sunday    |

BEGIN;
SET SCHEMA 'public';

UPDATE session_slot set session_template_reference = 'yyy.ieb.jnz' where session_template_reference = 'yzy.cja.bow' and slot_date >= '2024-09-30' and prison_id = (select id from prison where code = 'EYI');

UPDATE session_slot set session_template_reference = 'xyy.urm.bdw' where session_template_reference = 'xzy.ulq.pjv' and slot_date >= '2024-09-30' and prison_id = (select id from prison where code = 'EYI');

UPDATE session_slot set session_template_reference = 'myy.fwz.qsj' where session_template_reference = 'mzy.uyr.rew' and slot_date >= '2024-09-30' and prison_id = (select id from prison where code = 'EYI');

UPDATE session_slot set session_template_reference = 'qyy.cdj.ryo' where session_template_reference = 'qzy.cag.sgy' and slot_date >= '2024-09-30' and prison_id = (select id from prison where code = 'EYI');

UPDATE session_slot set session_template_reference = 'ryy.ior.zvd' where session_template_reference = 'rzy.ixl.las' and slot_date >= '2024-09-30' and prison_id = (select id from prison where code = 'EYI');

UPDATE session_slot set session_template_reference = 'byy.iqw.azq' where session_template_reference = 'bzy.inj.nsd' and slot_date >= '2024-09-30' and prison_id = (select id from prison where code = 'EYI');

END;
