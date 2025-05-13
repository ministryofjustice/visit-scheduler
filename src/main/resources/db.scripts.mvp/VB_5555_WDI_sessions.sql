-- Fix to update Wakefiled (WDI) from old session templates to new session templates

-- | new session | old session | day      |
-- | ----------- | ----------- | -------- |
-- | gdx.hqo.aba | ybx.idn.bmo | Friday   |
-- | jdx.ijx.yrl | xbx.ubj.eqg | Saturday |
-- | vdx.hyp.bzs | mbx.idw.swd | Sunday   |


BEGIN;
SET SCHEMA 'public';

UPDATE session_slot set session_template_reference = 'gdx.hqo.aba' where session_template_reference = 'ybx.idn.bmo' and slot_date >= '2025-05-12' and prison_id = (select id from prison where code = 'WDI');
UPDATE session_slot set session_template_reference = 'jdx.ijx.yrl' where session_template_reference = 'xbx.ubj.eqg' and slot_date >= '2025-05-12' and prison_id = (select id from prison where code = 'WDI');
UPDATE session_slot set session_template_reference = 'vdx.hyp.bzs' where session_template_reference = 'mbx.idw.swd' and slot_date >= '2025-05-12' and prison_id = (select id from prison where code = 'WDI');

END;
