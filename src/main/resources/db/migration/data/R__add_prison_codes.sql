INSERT INTO prison(code, active) SELECT 'HEI', true WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'HEI');
INSERT INTO prison(code, active) SELECT 'BLI', true WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'BLI');
INSERT INTO prison(code, active) SELECT 'CFI', true WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'CFI');
INSERT INTO prison(code, active) SELECT 'WWI', true WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'WWI');
INSERT INTO prison(code, active) SELECT 'PNI', true WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'PNI');