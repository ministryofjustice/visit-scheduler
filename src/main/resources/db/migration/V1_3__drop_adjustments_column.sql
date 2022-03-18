-- Migrate existing reasonable_adjustments to visit_support
INSERT INTO visit_support (visit_id, support_name, support_details)
SELECT id, 'OTHER', reasonable_adjustments
FROM visit where reasonable_adjustments IS NOT NULL;

ALTER TABLE visit
  DROP COLUMN reasonable_adjustments
;
