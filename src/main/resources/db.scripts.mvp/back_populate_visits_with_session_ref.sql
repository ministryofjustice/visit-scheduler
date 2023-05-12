BEGIN;

-- Make temporary table to contain visits with counts of session templates that match using the below where and join statements
    CREATE TEMP TABLE tmp_visits_with_sessions(visit_id int not null, session_template_count int not null);

-- insert visits with count of how many session templates match
    INSERT INTO tmp_visits_with_sessions(visit_id,session_template_count)
        select v.id,count(v.id)  from visit v
                                          join session_template st on
                st.start_time = v.visit_start::time and
                            st.end_time = v.visit_end::time and
                            st.prison_id = v.prison_id and
                            UPPER(TRIM(To_Char(v.visit_start , 'DAY'))) = st.day_of_week and
                            ((v.visit_restriction = 'OPEN' and st.open_capacity > 0) or (v.visit_restriction = 'CLOSED' and st.closed_capacity > 0))
        where (v.visit_start >= current_date
          and v.visit_start <= current_date + interval '28' day)
          and (st.valid_to_date is null or st.valid_to_date >= v.visit_start)
          and (st.valid_from_date <= v.visit_start)
        group by v.id
        order by v.id;

-- create table to contain one to one matches between visits and session templates
    CREATE TEMP TABLE tmp_visits_to_sessions(visit_id int not null,session_template_ref text not null);

-- insert data see tmp.session_template_count = 1;
    INSERT INTO tmp_visits_to_sessions(visit_id,session_template_ref)
        select v.id,st.reference  from visit v
                                           join session_template st on
                st.start_time = v.visit_start::time and
                            st.end_time = v.visit_end::time and
                            st.prison_id = v.prison_id and
                            UPPER(TRIM(To_Char(v.visit_start , 'DAY'))) = st.day_of_week and
                            ((v.visit_restriction = 'OPEN' and st.open_capacity > 0) or (v.visit_restriction = 'CLOSED' and st.closed_capacity > 0))
                        join tmp_visits_with_sessions tmp on tmp.visit_id = v.id
        where (v.visit_start >= current_date
          and v.visit_start <= current_date + interval '28' day)
          and (st.valid_to_date is null or st.valid_to_date >= v.visit_start)
          and (st.valid_from_date <= v.visit_start)
          and tmp.session_template_count = 1;

-- Create table to contain one to two matches between visits and session templates this is because of BiWeekly true
    CREATE TEMP TABLE tmp_visits_to_dup_sessions(visit_id int not null,session_template_ref text not null);

-- Insert biweekly visits into table
    INSERT INTO tmp_visits_to_dup_sessions(visit_id,session_template_ref)
        select v.id,st.reference  from visit v
                                           join session_template st on
                st.start_time = v.visit_start::time and
                            st.end_time = v.visit_end::time and
                            st.prison_id = v.prison_id and
                            UPPER(TRIM(To_Char(v.visit_start , 'DAY'))) = st.day_of_week and
                            ((v.visit_restriction = 'OPEN' and st.open_capacity > 0) or (v.visit_restriction = 'CLOSED' and st.closed_capacity > 0))
                        join tmp_visits_with_sessions tmp on tmp.visit_id = v.id
        where (v.visit_start >= current_date
          and v.visit_start <= current_date + interval '28' day)
          and (st.valid_to_date is null or st.valid_to_date >= v.visit_start)
          and (st.valid_from_date <= v.visit_start)
          and st.bi_weekly = true
          and tmp.session_template_count > 1;

-- Use mod operator to work out week from valid from date in relation to visit start date to insert data into correct mapping between visit and session template
    INSERT INTO tmp_visits_to_sessions(visit_id,session_template_ref)
        select v.id,st.reference from tmp_visits_to_dup_sessions tmp
                                          join visit v on v.id  =  tmp.visit_id
                                          join session_template st on tmp.session_template_ref = st.reference
        where mod(((v.visit_start::date - DATE_TRUNC('WEEK',st.valid_from_date)::date)/7),2) = 0
        order by v.id;

-- updates visits using data acquired
    UPDATE visit SET session_reference = tmp.session_template_ref
        FROM tmp_visits_to_sessions WHERE visit.id = tmp.visit_id

-- drop temporary tables
    DROP TABLE  tmp_visits_with_sessions;
    DROP TABLE  tmp_visits_to_sessions;
    DROP TABLE  tmp_visits_to_dup_sessions;

END;
