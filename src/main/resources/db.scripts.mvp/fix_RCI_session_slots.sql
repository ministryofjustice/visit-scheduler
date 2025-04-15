-- Fix to update Rochester (RCI) from old session templates to new session templates
-- | new session | old session | old session detail     |
-- | ----------- | ----------- | ---------------------- |
-- | qbx.uyx.ypp | sxa.fwa.mmr | Monday                 |
-- | rbx.hzl.vgj | nxa.img.enp | Tuesday                |
-- | bbx.iwo.gjv | pxa.fnl.asl | Wednesday              |
-- | obx.feq.rzp | exa.fgg.npg | Thursday               |
-- | dbx.cwl.add | zma.fnm.aro | Friday                 |
-- | sbx.fzq.dyl | vxa.hwy.qns | Saturday AM - I wing   |
-- | sbx.fzq.dyl | wxa.hpd.dyd | Saturday AM - enhanced |
-- | nbx.ixa.byv | jxa.cpq.dwz | Saturday PM            |
-- | pbx.hxe.ewn | kxa.uyz.rrd | Sunday                 |

BEGIN;
SET SCHEMA 'public';

UPDATE session_slot set session_template_reference = 'qbx.uyx.ypp' where session_template_reference = 'sxa.fwa.mmr' and slot_date >= '2025-04-18' and prison_id = (select id from prison where code = 'RCI');
UPDATE session_slot set session_template_reference = 'rbx.hzl.vgj' where session_template_reference = 'nxa.img.enp' and slot_date >= '2025-04-18' and prison_id = (select id from prison where code = 'RCI');
UPDATE session_slot set session_template_reference = 'bbx.iwo.gjv' where session_template_reference = 'pxa.fnl.asl' and slot_date >= '2025-04-18' and prison_id = (select id from prison where code = 'RCI');
UPDATE session_slot set session_template_reference = 'obx.feq.rzp' where session_template_reference = 'exa.fgg.npg' and slot_date >= '2025-04-18' and prison_id = (select id from prison where code = 'RCI');
UPDATE session_slot set session_template_reference = 'dbx.cwl.add' where session_template_reference = 'zma.fnm.aro' and slot_date >= '2025-04-18' and prison_id = (select id from prison where code = 'RCI');
UPDATE session_slot set session_template_reference = 'sbx.fzq.dyl' where session_template_reference = 'vxa.hwy.qns' and slot_date >= '2025-04-18' and prison_id = (select id from prison where code = 'RCI');
UPDATE session_slot set session_template_reference = 'sbx.fzq.dyl' where session_template_reference = 'wxa.hpd.dyd' and slot_date >= '2025-04-18' and prison_id = (select id from prison where code = 'RCI');
UPDATE session_slot set session_template_reference = 'nbx.ixa.byv' where session_template_reference = 'jxa.cpq.dwz' and slot_date >= '2025-04-18' and prison_id = (select id from prison where code = 'RCI');
UPDATE session_slot set session_template_reference = 'pbx.hxe.ewn' where session_template_reference = 'kxa.uyz.rrd' and slot_date >= '2025-04-18' and prison_id = (select id from prison where code = 'RCI');

END;
