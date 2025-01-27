-- All references below do not need adjusting
BEGIN;

SET SCHEMA 'public';

UPDATE session_template
SET
  visit_room = 'Visits Hall'
WHERE
  reference NOT IN (
  'lba.fgg.eyx',
  'bba.cqb.nmj',
  'dba.uyl.weo',
  'qba.fmm.smz',
  'xba.imz.mmd',
  'zny.fab.prv',
  'qdy.fbm.rbe',
  'nny.hjd.ewa',
  'pny.imd.qlj',
  'qny.isn.ojg',
  'xny.cbe.yyb',
  'ala.ilx.sqp',
  'lla.uoq.bzq',
  'qyl.uyg.xlr',
  'oyl.hzx.mgl',
  'myl.ide.wsm',
  'rdy.hxd.oxx',
  'eny.fld.bov',
  'gny.fnr.mjd',
  'ony.cda.bjm',
  'rny.imb.lxz',
  'bny.cvp.nsb',
  '-afe.dcc.a9',
  'qpy.fly.dgd',
  '-afe.dcc.ab',
  'bpy.cmg.ayv',
  '-afe.dcc.ad',
  'dpy.cjj.xgo',
  'qry.uge.wbb',
  'ory.isr.arb',
  'dry.uqw.rjd',
  'bry.cnx.ole',
  'rry.ugm.err',
  'vqy.unr.zoa',
  'wqy.iad.wnp',
  'mry.fmp.ayo',
  'gqy.fpl.vav',
  'eqy.iwx.gvy',
  'sdl.fxd.lna',
  'ndl.urw.qpj',
  'pdl.crd.oqr',
  'qgl.csq.aqb',
  'edl.fdb.awb',
  'jdl.hps.val',
  'wdl.idl.ypw',
  'vdl.hsm.exv'  
  );

END;
