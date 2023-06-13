BEGIN;

SET SCHEMA 'public';
update session_template set valid_to_date = '2023-06-09' where id = 77
  and visit_room = 'GRP1 SUPER ENHANCED'
  and prison_id = (select id from prison where code = 'BLI')
  and day_of_week = 'MONDAY';
END;
