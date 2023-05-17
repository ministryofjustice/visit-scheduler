INSERT INTO prison(code, active) SELECT 'HEI', true WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'HEI');
INSERT INTO prison(code, active) SELECT 'BLI', true WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'BLI');
INSERT INTO prison(code, active) SELECT 'CFI', true WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'CFI');
INSERT INTO prison(code, active) SELECT 'WWI', true WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'WWI');
INSERT INTO prison(code, active) SELECT 'PNI', true WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'PNI');
-- Disabled for now below this line
INSERT INTO prison(code, active) SELECT 'EWI', false WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'EWI');
INSERT INTO prison(code, active) SELECT 'DHI', false WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'DHI');
INSERT INTO prison(code, active) SELECT 'MHI', false WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'MHI');
INSERT INTO prison(code, active) SELECT 'BNI', false WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'BNI');
INSERT INTO prison(code, active) SELECT 'FNI', false WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'FNI');
INSERT INTO prison(code, active) SELECT 'LNI', false WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'LNI');
INSERT INTO prison(code, active) SELECT 'FHI', false WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'FHI');
INSERT INTO prison(code, active) SELECT 'ESI', false WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'ESI');
INSERT INTO prison(code, active) SELECT 'BSI', false WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'BSI');
INSERT INTO prison(code, active) SELECT 'AGI', false WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'AGI');
INSERT INTO prison(code, active) SELECT 'DMI', false WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'DMI');

-- Add Excl Dates for prisons

DELETE FROM prison_exclude_date;

-- HMP Morton Hall

INSERT INTO prison_exclude_date(prison_id, exclude_date) Select id,'2023-06-01' from prison where code = 'MHI';
INSERT INTO prison_exclude_date(prison_id, exclude_date) Select id,'2023-07-27' from prison where code = 'MHI';
INSERT INTO prison_exclude_date(prison_id, exclude_date) Select id,'2023-08-10' from prison where code = 'MHI';
INSERT INTO prison_exclude_date(prison_id, exclude_date) Select id,'2023-08-24' from prison where code = 'MHI';
INSERT INTO prison_exclude_date(prison_id, exclude_date) Select id,'2023-10-26' from prison where code = 'MHI';
INSERT INTO prison_exclude_date(prison_id, exclude_date) Select id,'2023-11-16' from prison where code = 'MHI';
INSERT INTO prison_exclude_date(prison_id, exclude_date) Select id,'2023-12-22' from prison where code = 'MHI';
INSERT INTO prison_exclude_date(prison_id, exclude_date) Select id,'2023-12-28' from prison where code = 'MHI';


-- HMP Preston

INSERT INTO prison_exclude_date(prison_id, exclude_date) Select id,'2023-05-16' from prison where code = 'PNI';
INSERT INTO prison_exclude_date(prison_id, exclude_date) Select id,'2023-06-15' from prison where code = 'PNI';
INSERT INTO prison_exclude_date(prison_id, exclude_date) Select id,'2023-07-18' from prison where code = 'PNI';
INSERT INTO prison_exclude_date(prison_id, exclude_date) Select id,'2023-09-19' from prison where code = 'PNI';
INSERT INTO prison_exclude_date(prison_id, exclude_date) Select id,'2023-10-17' from prison where code = 'PNI';
INSERT INTO prison_exclude_date(prison_id, exclude_date) Select id,'2023-11-21' from prison where code = 'PNI';
INSERT INTO prison_exclude_date(prison_id, exclude_date) Select id,'2023-12-12' from prison where code = 'PNI';