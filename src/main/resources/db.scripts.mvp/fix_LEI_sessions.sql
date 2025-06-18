-- Fix to update Leeds (LEI) and Feltham B (FMI) from old session template to new session template
--| new code    | old code    | Day       |
--| ----------- | ----------- | --------- |
--| qvx.hxm.abs | wdx.fgs.spz | Monday    |
--| rvx.iow.psg | asx.ibl.omx | Monday    |
--| bvx.hwe.ors | lsx.fyd.vse | Monday    |
--| ovx.fwz.bgz | kdx.ier.nqx | Tuesday   |
--| dvx.hla.zay | ysx.imd.ozj | Tuesday   |
--| svx.ijo.nxe | xsx.ivm.adl | Tuesday   |
--| nvx.cso.erb | msx.faj.woy | Wednesday |
--| pvx.uao.abd | qsx.fdq.gjr | Wednesday |
--| evx.fjw.mvy | vsx.cjm.pjq | Wednesday |
--| gvx.hpr.eqo | wsx.cyy.bmv | Wednesday |
--| jvx.izx.oay | ksx.cab.bxs | Wednesday |
--| vvx.fnl.ene | znx.fjq.ppq | Wednesday |
--| wvx.csx.ale | rsx.hog.anq | Friday    |
--| kvx.csz.xjl | bsx.uqx.gjm | Friday    |
--| zwx.inl.dyr | osx.fne.ppy | Saturday  |
--| lwx.csl.ngs | ssx.umv.zye | Saturday  |
--| ywx.fya.nge | nsx.uwj.eel | Saturday  |
--| xwx.fsr.msr | psx.fdb.pbg | Sunday    |
--| mwx.crg.sgm | esx.uvb.sba | Sunday    |
--| qwx.iwp.peg | gsx.hmr.jzs | Sunday    |
--| rwx.isj.mrx | jsx.inp.ngn | Sunday    |
--| awx.ceg.xyx | dsx.cav.xyr | Saturday  |
--| sam.cvb.dam | nox.iws.dwv | Wednesday |
--| nam.ugo.eas | gox.fnx.vrl | Saturday  |

BEGIN;
SET SCHEMA 'public';

-- UPDATE session_slot set session_template_reference = 'NEW' where session_template_reference = 'OLD' and prison_id = (select id from prison where code = 'LEI');
UPDATE session_slot set session_template_reference = 'qvx.hxm.abs' where session_template_reference = 'wdx.fgs.spz' and prison_id = (select id from prison where code = 'LEI');

UPDATE session_slot set session_template_reference = 'rvx.iow.psg' where session_template_reference = 'asx.ibl.omx' and prison_id = (select id from prison where code = 'LEI');

UPDATE session_slot set session_template_reference = 'bvx.hwe.ors' where session_template_reference = 'lsx.fyd.vse' and prison_id = (select id from prison where code = 'LEI');

UPDATE session_slot set session_template_reference = 'ovx.fwz.bgz' where session_template_reference = 'kdx.ier.nqx' and prison_id = (select id from prison where code = 'LEI');

UPDATE session_slot set session_template_reference = 'dvx.hla.zay' where session_template_reference = 'ysx.imd.ozj' and prison_id = (select id from prison where code = 'LEI');

UPDATE session_slot set session_template_reference = 'svx.ijo.nxe' where session_template_reference = 'xsx.ivm.adl' and prison_id = (select id from prison where code = 'LEI');

UPDATE session_slot set session_template_reference = 'nvx.cso.erb' where session_template_reference = 'msx.faj.woy' and prison_id = (select id from prison where code = 'LEI');

UPDATE session_slot set session_template_reference = 'pvx.uao.abd' where session_template_reference = 'qsx.fdq.gjr' and prison_id = (select id from prison where code = 'LEI');

UPDATE session_slot set session_template_reference = 'evx.fjw.mvy' where session_template_reference = 'vsx.cjm.pjq' and prison_id = (select id from prison where code = 'LEI');

UPDATE session_slot set session_template_reference = 'gvx.hpr.eqo' where session_template_reference = 'wsx.cyy.bmv' and prison_id = (select id from prison where code = 'LEI');

UPDATE session_slot set session_template_reference = 'jvx.izx.oay' where session_template_reference = 'ksx.cab.bxs' and prison_id = (select id from prison where code = 'LEI');

UPDATE session_slot set session_template_reference = 'vvx.fnl.ene' where session_template_reference = 'znx.fjq.ppq' and prison_id = (select id from prison where code = 'LEI');

UPDATE session_slot set session_template_reference = 'wvx.csx.ale' where session_template_reference = 'rsx.hog.anq' and prison_id = (select id from prison where code = 'LEI');

UPDATE session_slot set session_template_reference = 'kvx.csz.xjl' where session_template_reference = 'bsx.uqx.gjm' and prison_id = (select id from prison where code = 'LEI');

UPDATE session_slot set session_template_reference = 'zwx.inl.dyr' where session_template_reference = 'osx.fne.ppy' and prison_id = (select id from prison where code = 'LEI');

UPDATE session_slot set session_template_reference = 'lwx.csl.ngs' where session_template_reference = 'ssx.umv.zye' and prison_id = (select id from prison where code = 'LEI');

UPDATE session_slot set session_template_reference = 'ywx.fya.nge' where session_template_reference = 'nsx.uwj.eel' and prison_id = (select id from prison where code = 'LEI');

UPDATE session_slot set session_template_reference = 'xwx.fsr.msr' where session_template_reference = 'psx.fdb.pbg' and prison_id = (select id from prison where code = 'LEI');

UPDATE session_slot set session_template_reference = 'mwx.crg.sgm' where session_template_reference = 'esx.uvb.sba' and prison_id = (select id from prison where code = 'LEI');

UPDATE session_slot set session_template_reference = 'qwx.iwp.peg' where session_template_reference = 'gsx.hmr.jzs' and prison_id = (select id from prison where code = 'LEI');

UPDATE session_slot set session_template_reference = 'rwx.isj.mrx' where session_template_reference = 'jsx.inp.ngn' and prison_id = (select id from prison where code = 'LEI');

UPDATE session_slot set session_template_reference = 'awx.ceg.xyx' where session_template_reference = 'dsx.cav.xyr' and prison_id = (select id from prison where code = 'LEI');

UPDATE session_slot set session_template_reference = 'sam.cvb.dam' where session_template_reference = 'nox.iws.dwv' and prison_id = (select id from prison where code = 'FMI');

UPDATE session_slot set session_template_reference = 'nam.ugo.eas' where session_template_reference = 'gox.fnx.vrl' and prison_id = (select id from prison where code = 'FMI');

END;
