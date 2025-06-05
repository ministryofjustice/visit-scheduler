-- delete location group "wbc~gvl~sdv" from session templates for LEI
delete from session_to_location_group stlg where stlg.group_id in (
    select id from session_location_group slg where prison_id = (select id from prison where code = 'LEI')
    and reference = 'wbc~gvl~sdv'
) and stlg.session_template_id in (
    select id from session_template st where prison_id = (select id from prison where code = 'LEI')
)
