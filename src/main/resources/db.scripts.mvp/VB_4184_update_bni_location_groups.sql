BEGIN;

SET SCHEMA 'public';
update session_to_location_group set group_id = (
    select slg.id from session_location_group slg join prison p on slg.prison_id = p.id
    where reference = 'zxu~oem~xwp' and p.code = 'BNI'
)  where group_id = (
    select slg.id from session_location_group slg join prison p on slg.prison_id = p.id
    where reference = 'yyi~sye~mpm' and p.code = 'BNI');

END;
