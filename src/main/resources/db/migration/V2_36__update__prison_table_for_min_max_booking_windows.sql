ALTER TABLE prison ADD policy_notice_days_min integer NOT NULL DEFAULT 2;
ALTER TABLE prison ADD policy_notice_days_max integer NOT NULL DEFAULT 28;
ALTER TABLE prison ADD update_policy_notice_days_min integer NOT NULL DEFAULT 0;