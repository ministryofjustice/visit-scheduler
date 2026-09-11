BEGIN;

ALTER TABLE prison DROP policy_notice_days_min;
ALTER TABLE prison DROP policy_notice_days_max;
COMMIT;
