update application_contact set contact_phone = NULL where upper(contact_phone) = 'UNKNOWN';
update visit_contact set contact_phone = NULL where upper(contact_phone) = 'UNKNOWN';
