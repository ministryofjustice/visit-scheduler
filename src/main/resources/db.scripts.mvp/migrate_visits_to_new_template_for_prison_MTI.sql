-- https://dsdmoj.atlassian.net/browse/VB-3183 Migrate 2 visits from one session template to another
BEGIN;

    UPDATE visit
        SET session_template_reference = 'gsa.hlr.pmj'
        WHERE reference in ('dy-lp-xa-cl','mb-lp-xa-un') and session_template_reference = 'jqa.hmm.mmp';

END;
