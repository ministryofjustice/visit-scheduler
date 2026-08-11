alter table prison
    add column week_start_day varchar(10) not null default 'MONDAY',
    add column remand_visit_limit_per_week integer not null default 3;

alter table prison
    add constraint prison_week_start_day_valid
        check (week_start_day in ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'));

alter table prison
    add constraint remand_visit_limit_per_week_positive
        check (remand_visit_limit_per_week > 0);
