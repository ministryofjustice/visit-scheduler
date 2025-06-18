-- Fix to update Leeds (LEI) and Feltham B (FMI) from old session template to new session template

BEGIN;
SET SCHEMA 'public';

-- UPDATE session_slot set session_template_reference = 'NEW' where session_template_reference = 'OLD' and prison_id = (select id from prison where code = 'LEI');

UPDATE session_slot set session_template_reference = 'awx.ceg.xyx' where session_template_reference = 'dsx.cav.xyr' and prison_id = (select id from prison where code = 'LEI');

UPDATE session_slot set session_template_reference = 'sam.cvb.dam' where session_template_reference = 'nox.iws.dwv' and prison_id = (select id from prison where code = 'FMI');

UPDATE session_slot set session_template_reference = 'nam.ugo.eas' where session_template_reference = 'gox.fnx.vrl' and prison_id = (select id from prison where code = 'FMI');

END;
