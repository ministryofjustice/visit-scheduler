INSERT INTO prison(code, active) SELECT 'HEI', true WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'HEI');
INSERT INTO prison(code, active) SELECT 'BRS', true WHERE NOT EXISTS ( SELECT id FROM prison WHERE code = 'BRS');