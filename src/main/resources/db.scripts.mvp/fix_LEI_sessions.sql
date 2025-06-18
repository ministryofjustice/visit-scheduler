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

BEGIN;
SET SCHEMA 'public';

-- UPDATE session_slot set session_template_reference = 'NEW' where session_template_reference = 'OLD' and prison_id = (select id from prison where code = 'LEI');

UPDATE session_slot set session_template_reference = 'dsx.cav.xyr' where session_template_reference = 'awx.ceg.xyx' and prison_id = (select id from prison where code = 'LEI');

UPDATE session_slot set session_template_reference = 'nox.iws.dwv' where session_template_reference = 'sam.cvb.dam' and prison_id = (select id from prison where code = 'FMI');

UPDATE session_slot set session_template_reference = 'gox.fnx.vrl' where session_template_reference = 'nam.ugo.eas' and prison_id = (select id from prison where code = 'FMI');


END;
