ALTER TABLE prison_user_client ADD policy_notice_days_min integer NOT NULL DEFAULT 2;
ALTER TABLE prison_user_client ADD policy_notice_days_max integer NOT NULL DEFAULT 28;
