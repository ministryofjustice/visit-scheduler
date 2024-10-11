ALTER TABLE prison_exclude_date ADD actioned_by VARCHAR(60);
UPDATE prison_exclude_date set actioned_by = 'NOT_KNOWN' where actioned_by IS NULL;
ALTER TABLE prison_exclude_date ALTER COLUMN actioned_by SET NOT NULL;
