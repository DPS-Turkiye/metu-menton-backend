-- The ODTÜ edition starts from an empty database, so every Teknokent seed runs here too. After V37 and V84 only these
-- are left: the V60 admin and the V69 coordinator (Teknokent staff, linked to Teknokent's Firebase project) with their
-- V90 permission rows, and the empty V73 "Growth Circuit 2026" program with the V72 link of that coordinator to it.
-- ODTÜ's own staff come from APP_BOOTSTRAP_STAFF instead. V60, V69, V72 and V73 already ran and stay as they are.
--
-- A row goes only while nothing else points at it. On a new database nothing does; on a developer's database where
-- the seeded admin has already created programs or approved people, that row stays and the rest still goes. The
-- references are looked up in the catalogue rather than listed here, so a table added later is covered too.

CREATE FUNCTION pg_temp.v124_reference_count(target regclass, target_id uuid, skip_table regclass, skip_column text)
RETURNS bigint
LANGUAGE plpgsql
AS $$
DECLARE
    fk record;
    found bigint;
    total bigint := 0;
BEGIN
    -- A key over several columns is not looked into: it counts as a reference, so the row stays.
    IF EXISTS (SELECT 1 FROM pg_constraint c
               WHERE c.contype = 'f' AND c.confrelid = target AND array_length(c.conkey, 1) > 1) THEN
        RETURN 1;
    END IF;
    -- conparentid = 0: a partitioned table's key once, not again for each partition.
    FOR fk IN
        SELECT c.conrelid::regclass AS child, a.attname AS col
        FROM pg_constraint c
        JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = c.conkey[1]
        WHERE c.contype = 'f'
          AND c.confrelid = target
          AND c.conparentid = 0
    LOOP
        IF fk.child = skip_table AND fk.col = skip_column THEN
            CONTINUE;
        END IF;
        EXECUTE format('SELECT count(*) FROM %s WHERE %I = $1', fk.child, fk.col) INTO found USING target_id;
        total := total + found;
    END LOOP;
    RETURN total;
END
$$;

DO $$
DECLARE
    seed_program CONSTANT uuid := 'c1a00000-0000-4000-8000-000000000030';
    seed_link CONSTANT uuid := 'c1a00000-0000-4000-8000-000000000031';
    staff uuid;
BEGIN
    -- The program's only expected reference is the V72 link; any other coordinator, startup or member keeps it.
    IF EXISTS (SELECT 1 FROM program WHERE id = seed_program)
       AND NOT EXISTS (SELECT 1 FROM program_coordinator WHERE program_id = seed_program AND id <> seed_link)
       AND pg_temp.v124_reference_count('program', seed_program, 'program_coordinator', 'program_id') = 0 THEN
        DELETE FROM program WHERE id = seed_program; -- the V72 link goes with it (ON DELETE CASCADE)
    END IF;

    -- Each account's own permission rows go with it (ON DELETE CASCADE); anything else it is part of keeps it.
    -- The coordinator first: a permission the admin granted it would otherwise keep the admin.
    FOREACH staff IN ARRAY ARRAY[
        'a9d5c2e0-4b1f-4c8e-9f3a-5e7d2c1b0a02', -- V69 arya@coor.tnt
        'a9d5c2e0-4b1f-4c8e-9f3a-5e7d2c1b0a01'  -- V60 arya@admin.tnt
    ]::uuid[] LOOP
        IF pg_temp.v124_reference_count('app_user', staff, 'app_user_permission', 'user_id') = 0 THEN
            DELETE FROM app_user WHERE id = staff;
        END IF;
    END LOOP;
END
$$;

DROP FUNCTION pg_temp.v124_reference_count(regclass, uuid, regclass, text);
