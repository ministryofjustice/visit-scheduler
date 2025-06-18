-- Fix to update HOlme House (HHI) from old session template to new session template
--| new code    | old code    | Day       |
--| ----------- | ----------- | --------- |
--| dzm.idx.mpz | rex.cdj.nyn | Friday    |
--| bwx.iwx.jmj | mex.haa.zvr | Tuesday   |
--| owx.udn.jxs | xex.uyj.jzm | Tuesday   |
--| dwx.hyd.wqa | qex.fdq.oqo | Wednesday |
--| szm.ujz.oan | oex.fey.bme | Saturday  |
--| nzm.iwa.xoe | bex.cnq.lns | Saturday  |
--| ezm.uls.jme | sex.ulq.oae | Sunday    |
--| pzm.izg.jzm | dex.uyr.wjo | Sunday    |
--| swx.fym.xbv | nex.hza.lme | Thursday  |
--| nwx.has.vnz | eex.cjq.sjz | Thursday  |
--| pwx.fdo.rdq | gex.cny.bxr | Thursday  |

BEGIN;

SET SCHEMA 'public';

-- UPDATE session_slot set session_template_reference = 'NEW' where session_template_reference = 'OLD' and prison_id = (select id from prison where code = 'LEI');

UPDATE session_slot set session_template_reference = 'dzm.idx.mpz' where session_template_reference = 'rex.cdj.nyn' and prison_id = (select id from prison where code = 'HHI');

UPDATE session_slot set session_template_reference = 'bwx.iwx.jmj' where session_template_reference = 'mex.haa.zvr' and prison_id = (select id from prison where code = 'HHI');

UPDATE session_slot set session_template_reference = 'owx.udn.jxs' where session_template_reference = 'xex.uyj.jzm' and prison_id = (select id from prison where code = 'HHI');

UPDATE session_slot set session_template_reference = 'dwx.hyd.wqa' where session_template_reference = 'qex.fdq.oqo' and prison_id = (select id from prison where code = 'HHI');

UPDATE session_slot set session_template_reference = 'szm.ujz.oan' where session_template_reference = 'oex.fey.bme' and prison_id = (select id from prison where code = 'HHI');

UPDATE session_slot set session_template_reference = 'nzm.iwa.xoe' where session_template_reference = 'bex.cnq.lns' and prison_id = (select id from prison where code = 'HHI');

UPDATE session_slot set session_template_reference = 'ezm.uls.jme' where session_template_reference = 'sex.ulq.oae' and prison_id = (select id from prison where code = 'HHI');

UPDATE session_slot set session_template_reference = 'pzm.izg.jzm' where session_template_reference = 'dex.uyr.wjo' and prison_id = (select id from prison where code = 'HHI');

UPDATE session_slot set session_template_reference = 'swx.fym.xbv' where session_template_reference = 'nex.hza.lme' and prison_id = (select id from prison where code = 'HHI');

UPDATE session_slot set session_template_reference = 'nwx.has.vnz' where session_template_reference = 'eex.cjq.sjz' and prison_id = (select id from prison where code = 'HHI');

UPDATE session_slot set session_template_reference = 'pwx.fdo.rdq' where session_template_reference = 'gex.cny.bxr' and prison_id = (select id from prison where code = 'HHI');

END;