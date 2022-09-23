UPDATE visit SET application_reference = reference
WHERE application_reference IS NULL OR  LENGTH(application_reference) = 0;
