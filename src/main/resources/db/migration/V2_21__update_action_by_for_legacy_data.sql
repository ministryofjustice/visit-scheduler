UPDATE visit
    SET created_by = 'NOT_KNOWN_NOMIS'
    WHERE visit.created_by = 'hmpps-prisoner-from-nomis-migration-visits-3';

UPDATE visit
    SET cancelled_by = 'NOT_KNOWN_NOMIS'
    WHERE visit.cancelled_by = 'hmpps-prisoner-from-nomis-migration-visits-3'