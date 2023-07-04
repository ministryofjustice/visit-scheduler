ALTER TABLE session_template ADD weekly_frequency int CHECK (weekly_frequency > 0) NOT NULL DEFAULT 1;

UPDATE session_template	SET weekly_frequency = st.tmp_weekly_frequency
    FROM (SELECT CASE WHEN bi_weekly THEN 2 ELSE 1 END AS tmp_weekly_frequency, id  FROM session_template) AS st
        WHERE session_template.id = st.id;

ALTER TABLE session_template DROP bi_weekly;


