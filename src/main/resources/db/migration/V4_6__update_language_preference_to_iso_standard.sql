ALTER TABLE application_contact
    ALTER COLUMN language_preference SET DEFAULT 'en';

UPDATE application_contact
SET language_preference = LOWER(language_preference)
WHERE language_preference IS NOT NULL;

ALTER TABLE visit_contact
    ALTER COLUMN language_preference SET DEFAULT 'en';

UPDATE visit_contact
SET language_preference = LOWER(language_preference)
WHERE language_preference IS NOT NULL;