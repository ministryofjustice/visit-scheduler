-- Fix to update HMP HULL affected future dated visits from old session template to new session template
-- this is down to new session templates created on 2024-03-25 for HULL
-- | old session | new session |
-- | ----------- | ----------- |
-- | vda.fxy.nnj | gxl.fqd.szw |
-- | wda.cmx.exo | jxl.fyl.sbb |
-- | asa.ujv.oyy | kxl.has.qzq |
-- | lsa.czq.yjb | zml.uww.bpe |
-- | xsa.hrz.qvm | aml.ugb.xqw |
-- | ysa.upz.emw | lml.hxy.sad |
-- | msa.fvn.nlb | xml.cdg.plq |
-- | qsa.fpa.aob | yml.ulx.yap |
BEGIN;
SET SCHEMA 'public';

UPDATE session_slot set session_template_reference = 'gxl.fqd.szw' where session_template_reference = 'vda.fxy.nnj' and slot_date >= '2024-03-25' and prison_id = (select id from prison where code = 'HLI');
UPDATE session_slot set session_template_reference = 'jxl.fyl.sbb' where session_template_reference = 'wda.cmx.exo' and slot_date >= '2024-03-25' and prison_id = (select id from prison where code = 'HLI');
UPDATE session_slot set session_template_reference = 'kxl.has.qzq' where session_template_reference = 'asa.ujv.oyy' and slot_date >= '2024-03-25' and prison_id = (select id from prison where code = 'HLI');
UPDATE session_slot set session_template_reference = 'zml.uww.bpe' where session_template_reference = 'lsa.czq.yjb' and slot_date >= '2024-03-25' and prison_id = (select id from prison where code = 'HLI');
UPDATE session_slot set session_template_reference = 'aml.ugb.xqw' where session_template_reference = 'xsa.hrz.qvm' and slot_date >= '2024-03-25' and prison_id = (select id from prison where code = 'HLI');
UPDATE session_slot set session_template_reference = 'lml.hxy.sad' where session_template_reference = 'ysa.upz.emw' and slot_date >= '2024-03-25' and prison_id = (select id from prison where code = 'HLI');
UPDATE session_slot set session_template_reference = 'xml.cdg.plq' where session_template_reference = 'msa.fvn.nlb' and slot_date >= '2024-03-25' and prison_id = (select id from prison where code = 'HLI');
UPDATE session_slot set session_template_reference = 'yml.ulx.yap' where session_template_reference = 'qsa.fpa.aob' and slot_date >= '2024-03-25' and prison_id = (select id from prison where code = 'HLI');

END;
