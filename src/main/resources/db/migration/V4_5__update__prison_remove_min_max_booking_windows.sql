UPDATE prison_user_client as puc set policy_notice_days_min = p.policy_notice_days_min from prison as p where puc.prison_id = p.id;
UPDATE prison_user_client as puc set policy_notice_days_max = p.policy_notice_days_max from prison as p where puc.prison_id = p.id;

ALTER TABLE prison DROP policy_notice_days_min;
ALTER TABLE prison DROP policy_notice_days_max;
