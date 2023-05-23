

-- UTIL SCRIPT TO HELP DETECT ISSUES WITH SESSION MAPPING

    select  p.code,v.visit_start::time,st.start_time, UPPER(trim(to_char(visit_start, 'Day'))), st.day_of_week  from visit v
         join session_template st on st.reference = v.session_template_reference
         join legacy_data ld on ld.visit_id = v.id
         join prison p on p.id = st.prison_id
    where st.day_of_week != UPPER(trim(to_char(visit_start, 'Day'))) or
         st.visit_room != v.visit_room or
         st.visit_type  != st.visit_type or
         ((v.visit_restriction = 'OPEN' and st.open_capacity=0) or (v.visit_restriction = 'CLOSED' and st.closed_capacity=0)) or
         st.prison_id != v.prison_id;


    select v.reference,UPPER(trim(to_char(visit_start, 'Day'))),v.visit_restriction, v.visit_start::time,v.visit_end::time,v.visit_start::date as visit_date, v.prisoner_id
    from visit v
             join prison p on p.id = v.prison_id
             left join legacy_data ld on ld.visit_id = v.id
    where p.code = 'DMI' and ld.visit_id is null order by to_char(visit_start, 'Day');



-- E Wing

    update visit set session_template_reference = query.session_template_reference
        from (select st.reference as session_template_reference,v.reference
            from visit v
                join prison p on p.id = v.prison_id
                left join legacy_data ld on ld.visit_id = v.id
                join session_template st on st.day_of_week = UPPER(trim(to_char(visit_start, 'Day')))
                        and st.prison_id = v.prison_id
                        and st.start_time = v.visit_start::time
                        and st.end_time = v.visit_end::time
                        and ((v.visit_restriction = 'OPEN' and st.open_capacity>0) or (v.visit_restriction = 'CLOSED' and st.closed_capacity>0))
                join session_to_location_group link on link.session_template_id = st.id
                join session_location_group g on g.id = link.group_id
                join permitted_session_location loc on loc.group_id = g.id and loc.level_one_code = 'E'
            where p.code = 'DMI' and
            ld.visit_id is null  and
            v.prisoner_id in  ('A2234DN') and
            st.day_of_week = UPPER(trim(to_char(visit_start, 'Day')))) as query
    where query.reference = visit.reference

-- F WING


    update visit set session_template_reference = query.session_template_reference
        from (select st.reference as session_template_reference,v.reference
            from visit v
                join prison p on p.id = v.prison_id
                left join legacy_data ld on ld.visit_id = v.id
                join session_template st on st.day_of_week = UPPER(trim(to_char(visit_start, 'Day')))
                        and st.prison_id = v.prison_id
                        and st.start_time = v.visit_start::time
                        and st.end_time = v.visit_end::time
                        and ((v.visit_restriction = 'OPEN' and st.open_capacity>0) or (v.visit_restriction = 'CLOSED' and st.closed_capacity>0))
                join session_to_location_group link on link.session_template_id = st.id
                join session_location_group g on g.id = link.group_id
                join permitted_session_location loc on loc.group_id = g.id and loc.level_one_code = ''
            where p.code = 'DMI' and
            ld.visit_id is null  and
            v.prisoner_id in  ('A1758EX','A5396ER') and
            st.day_of_week = UPPER(trim(to_char(visit_start, 'Day')))) as query
    where query.reference = visit.reference

-- B WING

    update visit set session_template_reference = query.session_template_reference
        from (select st.reference as session_template_reference,v.reference
            from visit v
                join prison p on p.id = v.prison_id
                left join legacy_data ld on ld.visit_id = v.id
                join session_template st on st.day_of_week = UPPER(trim(to_char(visit_start, 'Day')))
                        and st.prison_id = v.prison_id
                        and st.start_time = v.visit_start::time
                        and st.end_time = v.visit_end::time
                        and ((v.visit_restriction = 'OPEN' and st.open_capacity>0) or (v.visit_restriction = 'CLOSED' and st.closed_capacity>0))
                join session_to_location_group link on link.session_template_id = st.id
                join session_location_group g on g.id = link.group_id
                join permitted_session_location loc on loc.group_id = g.id and loc.level_one_code = 'B'
            where p.code = 'DMI' and
            ld.visit_id is null  and
            v.prisoner_id in  ('A2009CG','A1364EY','A6590EX') and
            st.day_of_week = UPPER(trim(to_char(visit_start, 'Day')))) as query
    where query.reference = visit.reference

-- A WING

    update visit set session_template_reference = query.session_template_reference
        from (select st.reference as session_template_reference,v.reference
            from visit v
                join prison p on p.id = v.prison_id
                left join legacy_data ld on ld.visit_id = v.id
                join session_template st on st.day_of_week = UPPER(trim(to_char(visit_start, 'Day')))
                        and st.prison_id = v.prison_id
                        and st.start_time = v.visit_start::time
                        and st.end_time = v.visit_end::time
                        and ((v.visit_restriction = 'OPEN' and st.open_capacity>0) or (v.visit_restriction = 'CLOSED' and st.closed_capacity>0))
                join session_to_location_group link on link.session_template_id = st.id
                join session_location_group g on g.id = link.group_id
                join permitted_session_location loc on loc.group_id = g.id and loc.level_one_code = 'A'
            where p.code = 'DMI' and
            ld.visit_id is null  and
            v.prisoner_id in  ('A0041AG','A9646CY','A0876DJ','A9049AD') and
            st.day_of_week = UPPER(trim(to_char(visit_start, 'Day')))) as query
    where query.reference = visit.reference

-- D WING


    update visit set session_template_reference = query.session_template_reference
        from (select st.reference as session_template_reference,v.reference
            from visit v
                join prison p on p.id = v.prison_id
                left join legacy_data ld on ld.visit_id = v.id
                join session_template st on st.day_of_week = UPPER(trim(to_char(visit_start, 'Day')))
                        and st.prison_id = v.prison_id
                        and st.start_time = v.visit_start::time
                        and st.end_time = v.visit_end::time
                        and ((v.visit_restriction = 'OPEN' and st.open_capacity>0) or (v.visit_restriction = 'CLOSED' and st.closed_capacity>0))
                join session_to_location_group link on link.session_template_id = st.id
                join session_location_group g on g.id = link.group_id
                join permitted_session_location loc on loc.group_id = g.id and loc.level_one_code = 'D'
            where p.code = 'DMI' and
            ld.visit_id is null  and
            v.prisoner_id in  ('A3590EW','A8062AQ','A4790EL','A5774DN','A9021CD','A8930DM','A7603CV','A9502AP') and
            st.day_of_week = UPPER(trim(to_char(visit_start, 'Day')))) as query
    where query.reference = v.reference


