-- Fix to update HMP HULL affected future dated visits from old session template to new session template
-- this is down to new session templates created on 2024-04-02 for HULL
-- | old session | new session |
-- | ----------- | ----------- |
-- | gxl.fqd.szw | mml.hpg.zzq |
-- | jxl.fyl.sbb | qml.hwq.amm |
-- | kxl.has.qzq | rml.hbv.sbo |
-- | zml.uww.bpe | bml.uql.azn |
-- | aml.ugb.xqw | oml.hqp.odn |
-- | lml.hxy.sad | dml.ioa.asr |
-- | xml.cdg.plq | nml.usr.eyv |
-- | yml.ulx.yap | sml.ivg.oel |
BEGIN;
SET SCHEMA 'public';

UPDATE session_slot set session_template_reference = 'mml.hpg.zzq' where session_template_reference = 'gxl.fqd.szw' and slot_date >= '2024-04-02' and prison_id = (select id from prison where code = 'HLI');
UPDATE session_slot set session_template_reference = 'qml.hwq.amm' where session_template_reference = 'jxl.fyl.sbb' and slot_date >= '2024-04-02' and prison_id = (select id from prison where code = 'HLI');
UPDATE session_slot set session_template_reference = 'rml.hbv.sbo' where session_template_reference = 'kxl.has.qzq' and slot_date >= '2024-04-02' and prison_id = (select id from prison where code = 'HLI');
UPDATE session_slot set session_template_reference = 'bml.uql.azn' where session_template_reference = 'zml.uww.bpe' and slot_date >= '2024-04-02' and prison_id = (select id from prison where code = 'HLI');
UPDATE session_slot set session_template_reference = 'oml.hqp.odn' where session_template_reference = 'aml.ugb.xqw' and slot_date >= '2024-04-02' and prison_id = (select id from prison where code = 'HLI');
UPDATE session_slot set session_template_reference = 'dml.ioa.asr' where session_template_reference = 'lml.hxy.sad' and slot_date >= '2024-04-02' and prison_id = (select id from prison where code = 'HLI');
UPDATE session_slot set session_template_reference = 'nml.usr.eyv' where session_template_reference = 'xml.cdg.plq' and slot_date >= '2024-04-02' and prison_id = (select id from prison where code = 'HLI');
UPDATE session_slot set session_template_reference = 'sml.ivg.oel' where session_template_reference = 'yml.ulx.yap' and slot_date >= '2024-04-02' and prison_id = (select id from prison where code = 'HLI');

END;
