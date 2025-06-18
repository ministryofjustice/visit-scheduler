-- Begin work on "Request a visit" Feature. Add new "sub status" column to Visit table
-- to allow us to differentiate between instant bookings and requested bookings.

-- Add sub_status column
ALTER TABLE visit ADD visit_sub_status VARCHAR(50);

-- Back fill existing rows with 'Auto_Approved'
UPDATE visit SET visit_sub_status = 'AUTO_APPROVED';

-- Set NOT NULL
ALTER TABLE visit ALTER COLUMN visit_sub_status SET NOT NULL;

-- Add CHECK constraint to enforce allowed combinations of visit_status and sub_status
ALTER TABLE visit ADD CONSTRAINT chk_visit_sub_status
    CHECK (
        (visit_status = 'BOOKED' AND visit_sub_status IN ('AUTO_APPROVED', 'REQUESTED', 'APPROVED'))
            OR
        (visit_status = 'CANCELLED' AND visit_sub_status IN ('CANCELLED', 'WITHDRAWN', 'AUTO_REJECTED', 'REJECTED'))
        );
