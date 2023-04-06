ALTER TABLE legacy_data ADD visit_room VARCHAR(255);

UPDATE legacy_data
    SET visit_room = subquery.visit_room
        FROM (SELECT v.id,v.visit_room FROM visit v JOIN legacy_data ld ON ld.visit_id = v.id) AS subquery
    WHERE legacy_data.visit_id = subquery.id;

ALTER TABLE visit RENAME COLUMN visit_room TO capacity_group;
ALTER TABLE session_template RENAME COLUMN visit_room TO capacity_group;