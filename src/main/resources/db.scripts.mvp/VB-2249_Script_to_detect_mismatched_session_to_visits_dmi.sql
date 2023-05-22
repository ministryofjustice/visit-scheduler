

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


--REFERENCE	DAYOFWEEK	RESTRICTION	VISIT_START	VISIT_END	VISIT_DATE	PRISONER_ID LOCATION
-- E Wing
--ly-xr-pi-sq	FRIDAY	    OPEN	    13:45:00	15:45:00	2023-06-02	A2234DN     E-3-028

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
--wg-yr-pu-qo	THURSDAY	OPEN	    13:45:00	15:45:00	2023-05-25	A1758EX     F-2-007
--jl-xr-pc-sa	THURSDAY	OPEN	    13:45:00	15:45:00	2023-06-01	A5396ER     F-3-004


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
--mj-yr-ph-ol	SATURDAY	OPEN	    13:45:00	15:45:00	2023-05-27	A2009CG     B-2-006
--dl-xr-ph-ap	SUNDAY	    OPEN	    13:45:00	15:45:00	2023-06-11	A1364EY     B-2-020
--lw-yr-pi-dj	SUNDAY	    OPEN	    13:45:00	15:45:00	2023-05-28	A6590EX     B-3-004
--vv-yr-pu-ja	TUESDAY	    OPEN	    13:45:00	15:45:00	2023-05-23	A6590EX     B-3-004
--zl-xr-ph-rm	TUESDAY	    OPEN	    13:45:00	15:45:00	2023-05-30	A2009CG     B-2-006

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
--my-xr-pu-ym	FRIDAY	    OPEN	    13:45:00	15:45:00	2023-06-02	A0041AG     A-4-004
--rj-yr-pc-vq	SUNDAY	    OPEN	    13:45:00	15:45:00	2023-05-28	A9646CY     A-3-022
--sj-yr-pu-zl	SUNDAY	    OPEN	    13:45:00	15:45:00	2023-05-28	A0876DJ     A-4-005
--bj-yr-pf-wm	TUESDAY	    OPEN	    13:45:00	15:45:00	2023-05-23	A0876DJ     A-4-005
--dg-yr-ph-me	TUESDAY	    OPEN	    13:45:00	15:45:00	2023-05-23	A9049AD     A-3-013

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
--gx-xr-pf-my	SATURDAY	OPEN	    13:45:00	15:45:00	2023-06-03	A3590EW     D-2-024
--bx-xr-pf-nm	FRIDAY	    OPEN	    13:45:00	15:45:00	2023-05-26	A8062AQ     D-3-028
--dj-ar-pc-yp	SATURDAY	OPEN	    09:30:00	11:30:00	2023-05-20	A4790EL     D-2-030
--aj-yr-pu-bg	SATURDAY	OPEN	    13:45:00	15:45:00	2023-05-27	A5774DN     D-2-018
--eb-ar-pi-rs	TUESDAY	    OPEN	    13:45:00	15:45:00	2023-05-23	A9021CD     D-3-011
--ng-yr-pf-ee	TUESDAY	    OPEN	    13:45:00	15:45:00	2023-05-23	A8930DM     D-3-031
--pg-yr-pu-rs	TUESDAY	    OPEN	    13:45:00	15:45:00	2023-05-23	A7603CV     D-1-016
--jg-yr-ph-mp	WEDNESDAY	OPEN	    13:45:00	15:45:00	2023-05-24	A9502AP     D-2-029


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


