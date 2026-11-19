-- The coordinator's İŞKUR intern list (weekend student launch, part B). Until now every approved student of a peer
-- program counted as an intern; from here on only the accounts whose e-mail is on their program's list do. Everyone
-- else signed up to the program is a plain student: no İŞKUR onboarding, workspace or placement pool. An address can be
-- listed before its owner signs up; they are an intern as soon as their account joins the program.

CREATE TABLE iskur_intern_email (
    id          UUID PRIMARY KEY,
    program_id  UUID NOT NULL REFERENCES program (id) ON DELETE CASCADE,
    -- Stored trimmed and lowercased, so the unique key and the account match are exact.
    email       VARCHAR(320) NOT NULL,
    added_by    UUID REFERENCES app_user (id) ON DELETE SET NULL,
    added_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_iskur_intern_email UNIQUE (program_id, email),
    CONSTRAINT ck_iskur_intern_email_lower CHECK (email = lower(btrim(email)))
);

CREATE INDEX ix_iskur_intern_email_added_by ON iskur_intern_email (added_by);
-- currentProgram() asks "is this address listed in that program" for a signed-in student; the unique key's
-- (program_id, email) order already serves it, so no second index on email.

-- Keep everyone the workspace already treats as an intern: the placed, and those who started the onboarding form.
-- Without this, a placed person would lose their team screens the moment the list took over.
INSERT INTO iskur_intern_email (id, program_id, email, added_by, added_at)
SELECT gen_random_uuid(), known.program_id, known.email, NULL, now()
FROM (
    SELECT p.program_id, lower(btrim(u.email)) AS email
    FROM iskur_placement p
    JOIN app_user u ON u.id = p.user_id
    WHERE u.email IS NOT NULL AND btrim(u.email) <> ''
    UNION
    SELECT o.program_id, lower(btrim(u.email)) AS email
    FROM iskur_onboarding o
    JOIN app_user u ON u.id = o.user_id
    WHERE u.email IS NOT NULL AND btrim(u.email) <> ''
) AS known
ON CONFLICT (program_id, email) DO NOTHING;
