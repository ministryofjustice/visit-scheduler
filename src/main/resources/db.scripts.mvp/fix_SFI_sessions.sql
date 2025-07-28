-- Fix to update Stafford (SFI) from old session template to new session template
-- | new code    | old code    | day      |
-- | ----------- | ----------- | -------- |
-- | srm.hja.gby | owy.ubb.zjn | Wedesday |
-- | srm.hja.gby | dwy.ibw.rlg | Wedesday |
-- | srm.hja.gby | swy.cvp.exb | Wedesday |
-- | nrm.cwy.prj | nwy.hpx.xnp | Thursday |
-- | nrm.cwy.prj | pwy.inw.yjs | Thursday |
-- | nrm.cwy.prj | ewy.ibw.weg | Thursday |
-- | prm.hjd.erl | gwy.haw.xzz | Saturday |
-- | prm.hjd.erl | jwy.fjg.pew | Saturday |
-- | prm.hjd.erl | vwy.ule.vra | Saturday |
-- | erm.iqx.gxz | wwy.fmj.vsa | Sunday   |
-- | erm.iqx.gxz | kwy.hjq.qoq | Sunday   |
-- | erm.iqx.gxz | zky.upr.ezr | Sunday   |

BEGIN;

SET SCHEMA 'public';

-- UPDATE session_slot set session_template_reference = 'NEW' where session_template_reference = 'OLD' and prison_id = (select id from prison where code = 'LEI');

UPDATE session_slot set session_template_reference = 'srm.hja.gby' where session_template_reference = 'owy.ubb.zjn' and prison_id = (select id from prison where code = 'SFI');

UPDATE session_slot set session_template_reference = 'srm.hja.gby' where session_template_reference = 'dwy.ibw.rlg' and prison_id = (select id from prison where code = 'SFI');

UPDATE session_slot set session_template_reference = 'srm.hja.gby' where session_template_reference = 'swy.cvp.exb' and prison_id = (select id from prison where code = 'SFI');

UPDATE session_slot set session_template_reference = 'nrm.cwy.prj' where session_template_reference = 'nwy.hpx.xnp' and prison_id = (select id from prison where code = 'SFI');

UPDATE session_slot set session_template_reference = 'nrm.cwy.prj' where session_template_reference = 'pwy.inw.yjs' and prison_id = (select id from prison where code = 'SFI');

UPDATE session_slot set session_template_reference = 'nrm.cwy.prj' where session_template_reference = 'ewy.ibw.weg' and prison_id = (select id from prison where code = 'SFI');

UPDATE session_slot set session_template_reference = 'prm.hjd.erl' where session_template_reference = 'gwy.haw.xzz' and prison_id = (select id from prison where code = 'SFI');

UPDATE session_slot set session_template_reference = 'prm.hjd.erl' where session_template_reference = 'jwy.fjg.pew' and prison_id = (select id from prison where code = 'SFI');

UPDATE session_slot set session_template_reference = 'prm.hjd.erl' where session_template_reference = 'vwy.ule.vra' and prison_id = (select id from prison where code = 'SFI');

UPDATE session_slot set session_template_reference = 'erm.iqx.gxz' where session_template_reference = 'wwy.fmj.vsa' and prison_id = (select id from prison where code = 'SFI');

UPDATE session_slot set session_template_reference = 'erm.iqx.gxz' where session_template_reference = 'kwy.hjq.qoq' and prison_id = (select id from prison where code = 'SFI');

UPDATE session_slot set session_template_reference = 'erm.iqx.gxz' where session_template_reference = 'zky.upr.ezr' and prison_id = (select id from prison where code = 'SFI');

END;