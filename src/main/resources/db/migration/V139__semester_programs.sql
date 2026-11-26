-- Hocam semester programs (26 Nov): an ODTÜ program becomes a semester with three switches, Akran Mentörlüğü,
-- Office Hours and İŞKUR (İŞKUR only next to peer mentoring). Startup mentoring programs (Teknokent) are untouched.
-- Merge after V138. Nothing is deleted: the programs keep their ids, so every student (app_user.program_id),
-- mentor row, mentorship, request, report, placement and badge stays where it is.

ALTER TABLE program ADD COLUMN peer_mentoring_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE program ADD COLUMN office_hours_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE program ADD COLUMN iskur_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE program ADD CONSTRAINT chk_program_iskur_needs_peer
    CHECK (NOT iskur_enabled OR peer_mentoring_enabled);

ALTER TABLE program DROP CONSTRAINT chk_program_type;
ALTER TABLE program ADD CONSTRAINT chk_program_type
    CHECK (program_type IN ('STARTUP_MENTORING', 'PEER_MENTORING', 'OFFICE_HOURS', 'SEMESTER'));
ALTER TABLE platform_feedback DROP CONSTRAINT chk_platform_feedback_program_type;
ALTER TABLE platform_feedback ADD CONSTRAINT chk_platform_feedback_program_type
    CHECK (program_type IS NULL OR program_type IN ('STARTUP_MENTORING', 'PEER_MENTORING', 'OFFICE_HOURS',
                                                    'SEMESTER'));

-- Which features each coordinator of a program runs. Keyed by person, not by program_coordinator row, because the
-- creator of a program counts as its coordinator without a row there (V61).
CREATE TABLE program_coordinator_feature (
    program_id UUID NOT NULL REFERENCES program (id) ON DELETE CASCADE,
    user_id    UUID NOT NULL REFERENCES app_user (id),
    feature    VARCHAR(32) NOT NULL,
    PRIMARY KEY (program_id, user_id, feature),
    CONSTRAINT chk_program_coordinator_feature CHECK (feature IN ('PEER_MENTORING', 'OFFICE_HOURS', 'ISKUR'))
);
CREATE INDEX idx_program_coordinator_feature_user ON program_coordinator_feature (user_id);

-- Conversion. The one running peer program (if there is exactly one) becomes the running semester; running
-- Office Hours programs fold into it: their coordinators join it for Office Hours and they are completed.
CREATE TEMPORARY TABLE v139_semester ON COMMIT DROP AS
SELECT id FROM program
WHERE program_type = 'PEER_MENTORING' AND status = 'ACTIVE'
  AND (SELECT COUNT(*) FROM program WHERE program_type = 'PEER_MENTORING' AND status = 'ACTIVE') = 1;

CREATE TEMPORARY TABLE v139_folded ON COMMIT DROP AS
SELECT id, created_by FROM program
WHERE program_type = 'OFFICE_HOURS' AND status = 'ACTIVE' AND EXISTS (SELECT 1 FROM v139_semester);

-- Coordinators keep what their program ran: a peer program's run peer mentoring and İŞKUR, an Office Hours
-- program's run Office Hours.
INSERT INTO program_coordinator_feature (program_id, user_id, feature)
SELECT pc.program_id, pc.user_id, f.feature
FROM program_coordinator pc
JOIN program p ON p.id = pc.program_id
CROSS JOIN (VALUES ('PEER_MENTORING'), ('ISKUR')) AS f (feature)
WHERE p.program_type = 'PEER_MENTORING';

INSERT INTO program_coordinator_feature (program_id, user_id, feature)
SELECT pc.program_id, pc.user_id, 'OFFICE_HOURS'
FROM program_coordinator pc
JOIN program p ON p.id = pc.program_id
WHERE p.program_type = 'OFFICE_HOURS';

-- The folded programs' coordinators join the semester (once each) and run its Office Hours.
INSERT INTO program_coordinator (id, program_id, user_id, added_by, created_at)
SELECT gen_random_uuid(), s.id, moved.user_id, moved.added_by, NOW()
FROM v139_semester s
CROSS JOIN (
    SELECT pc.user_id, MIN(f.created_by::text)::uuid AS added_by
    FROM program_coordinator pc
    JOIN v139_folded f ON f.id = pc.program_id
    GROUP BY pc.user_id
) AS moved
WHERE NOT EXISTS (
    SELECT 1 FROM program_coordinator existing
    WHERE existing.program_id = s.id AND existing.user_id = moved.user_id
);

INSERT INTO program_coordinator_feature (program_id, user_id, feature)
SELECT DISTINCT s.id, pc.user_id, 'OFFICE_HOURS'
FROM v139_semester s
CROSS JOIN program_coordinator pc
JOIN v139_folded f ON f.id = pc.program_id
WHERE NOT EXISTS (
    SELECT 1 FROM program_coordinator_feature existing
    WHERE existing.program_id = s.id AND existing.user_id = pc.user_id AND existing.feature = 'OFFICE_HOURS'
);

UPDATE program SET status = 'COMPLETED' WHERE id IN (SELECT id FROM v139_folded);

-- Every ODTÜ program becomes a semester with the switches its type implied; the running semester also gets Office
-- Hours when a running Office Hours program folded into it.
UPDATE program
SET peer_mentoring_enabled = TRUE,
    iskur_enabled = TRUE,
    office_hours_enabled = (id IN (SELECT id FROM v139_semester) AND EXISTS (SELECT 1 FROM v139_folded)),
    program_type = 'SEMESTER'
WHERE program_type = 'PEER_MENTORING';

UPDATE program
SET office_hours_enabled = TRUE,
    program_type = 'SEMESTER'
WHERE program_type = 'OFFICE_HOURS';
