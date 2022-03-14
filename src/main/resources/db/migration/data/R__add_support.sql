DELETE FROM support;

INSERT INTO support(code, name, description)
VALUES
    (1010, 'WHEELCHAIR', 'Wheelchair ramp'),
    (1020, 'INDUCTION_LOOP','Portable induction loop for people with hearing aids'),
    (1030, 'BSL_INTERPRETER','British Sign Language (BSL) Interpreter'),
    (1040, 'MASK_EXEMPT','Face covering exemption'),
    (1050, 'OTHER','Other')
;
