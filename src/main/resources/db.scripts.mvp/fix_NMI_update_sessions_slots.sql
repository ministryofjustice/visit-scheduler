-- Fix to update NOttingham visits from old session templates to new session templates
-- | new session | old session |
-- | ----------- | ----------- |
-- | mrx.urp.ypw | wyy.uvq.sas |
-- | qrx.hgx.jml | kyy.coq.vwd |
-- | orx.fza.yag | zxy.ujx.gjn |
-- | srx.hbx.aes | lxy.fxp.sse |
-- | prx.irx.rla | yxy.ubm.qqe |
-- | grx.col.wms | xxy.ubv.vgb |
-- | jrx.flq.qee | mxy.hja.rrm |
-- | wrx.fss.glj | rxy.hyn.dej |
-- | vrx.uvb.jgs | qxy.ilr.vpv |

BEGIN;
SET SCHEMA 'public';

UPDATE session_slot set session_template_reference = 'mrx.urp.ypw' where session_template_reference = 'wyy.uvq.sas' and slot_date >= '2025-04-01' and prison_id = (select id from prison where code = 'NMI');
UPDATE session_slot set session_template_reference = 'qrx.hgx.jml' where session_template_reference = 'kyy.coq.vwd' and slot_date >= '2025-04-01' and prison_id = (select id from prison where code = 'NMI');
UPDATE session_slot set session_template_reference = 'orx.fza.yag' where session_template_reference = 'zxy.ujx.gjn' and slot_date >= '2025-04-01' and prison_id = (select id from prison where code = 'NMI');
UPDATE session_slot set session_template_reference = 'srx.hbx.aes' where session_template_reference = 'lxy.fxp.sse' and slot_date >= '2025-04-01' and prison_id = (select id from prison where code = 'NMI');
UPDATE session_slot set session_template_reference = 'prx.irx.rla' where session_template_reference = 'yxy.ubm.qqe' and slot_date >= '2025-04-01' and prison_id = (select id from prison where code = 'NMI');
UPDATE session_slot set session_template_reference = 'grx.col.wms' where session_template_reference = 'xxy.ubv.vgb' and slot_date >= '2025-04-01' and prison_id = (select id from prison where code = 'NMI');
UPDATE session_slot set session_template_reference = 'jrx.flq.qee' where session_template_reference = 'mxy.hja.rrm' and slot_date >= '2025-04-01' and prison_id = (select id from prison where code = 'NMI');
UPDATE session_slot set session_template_reference = 'wrx.fss.glj' where session_template_reference = 'rxy.hyn.dej' and slot_date >= '2025-04-01' and prison_id = (select id from prison where code = 'NMI');
UPDATE session_slot set session_template_reference = 'vrx.uvb.jgs' where session_template_reference = 'qxy.ilr.vpv' and slot_date >= '2025-04-01' and prison_id = (select id from prison where code = 'NMI');

END;