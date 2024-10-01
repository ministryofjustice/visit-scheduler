-- Fix to update Norwich (NWI) from old session template to new session template
-- | new session | old session | day       |
-- | ----------- | ----------- | --------- |
-- | oyy.fgb.gwz | dly.hgz.xyg | Thursday  |
-- | dyy.ujo.sje | nly.uvp.wmq | Sunday    |

BEGIN;
SET SCHEMA 'public';

UPDATE session_slot set session_template_reference = 'oyy.fgb.gwz' where session_template_reference = 'dly.hgz.xyg' and slot_date >= '2024-09-30' and prison_id = (select id from prison where code = 'NWI');

UPDATE session_slot set session_template_reference = 'dyy.ujo.sje' where session_template_reference = 'nly.uvp.wmq' and slot_date >= '2024-09-30' and prison_id = (select id from prison where code = 'NWI');

END;
