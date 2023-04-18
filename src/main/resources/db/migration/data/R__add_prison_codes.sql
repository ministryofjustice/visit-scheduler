INSERT INTO prison(code, active) SELECT 'HEI', true WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'HEI');
INSERT INTO prison(code, active) SELECT 'BLI', true WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'BLI');
INSERT INTO prison(code, active) SELECT 'CFI', true WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'CFI');
INSERT INTO prison(code, active) SELECT 'WWI', true WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'WWI');
INSERT INTO prison(code, active) SELECT 'PNI', true WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'PNI');
INSERT INTO prison(code, active) SELECT 'EWI', true WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'EWI');
INSERT INTO prison(code, active) SELECT 'DHI', true WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'DHI');
INSERT INTO prison(code, active) SELECT 'MHI', true WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'MHI');
INSERT INTO prison(code, active) SELECT 'BNI', true WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'BNI');
-- Disabled for now below this line
INSERT INTO prison(code, active) SELECT 'LNI', false WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'LNI');
INSERT INTO prison(code, active) SELECT 'FHI', false WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'FHI');
INSERT INTO prison(code, active) SELECT 'ESI', false WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'ESI');
INSERT INTO prison(code, active) SELECT 'DMI', false WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'DMI');
INSERT INTO prison(code, active) SELECT 'BSI', false WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'BSI');
INSERT INTO prison(code, active) SELECT 'AGI', false WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'AGI');
INSERT INTO prison(code, active) SELECT 'FNI', false WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'FNI');
