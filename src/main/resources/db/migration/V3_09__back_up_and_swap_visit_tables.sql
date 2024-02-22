-- back up data from old visits to equivalent backup tables

ALTER TABLE visit RENAME TO visit_backup;

CREATE TABLE visit_contact_backup AS TABLE visit_contact WITH DATA;
CREATE TABLE visit_support_backup AS TABLE visit_support WITH DATA;
CREATE TABLE visit_visitors_backup AS TABLE visit_visitor WITH DATA;
CREATE TABLE visit_notes_backup AS TABLE visit_notes WITH DATA;

ALTER TABLE tmp_visit RENAME TO visit;