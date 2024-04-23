-- Add text to event_audit to save any reasons / notes.
ALTER TABLE prison ADD max_total_visitors integer NOT NULL DEFAULT 10;
ALTER TABLE prison ADD max_adult_visitors integer NOT NULL DEFAULT 10;
ALTER TABLE prison ADD max_child_visitors integer NOT NULL DEFAULT 10;
ALTER TABLE prison ADD adult_age_years    integer NOT NULL DEFAULT 18;

