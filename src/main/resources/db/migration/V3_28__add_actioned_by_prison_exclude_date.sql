ALTER TABLE prison_exclude_date ADD actioned_by VARCHAR(60);
update prison_exclude_date set actioned_by = 'NOT_KNOWN';
ALTER TABLE prison_exclude_date ALTER COLUMN actioned_by SET NOT NULL;
