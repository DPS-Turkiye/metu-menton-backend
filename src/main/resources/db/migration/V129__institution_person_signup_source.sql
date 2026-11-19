-- ODTÜ students who sign up with their own @metu.edu.tr address keep the department and year they typed in
-- institution_person, where the Akran roster reads them, marked SIGNUP so a later institution login overwrites them.
ALTER TABLE institution_person DROP CONSTRAINT chk_institution_person_source;
ALTER TABLE institution_person ADD CONSTRAINT chk_institution_person_source
    CHECK (source IN ('LOGIN', 'IMPORT', 'DEV_STUB', 'SIGNUP'));
