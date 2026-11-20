-- İŞKUR weekly work, two follow-ups of V127.

-- 1. A week kept only its last decision: sending it again cleared the decision and the objection to it. Every step is
-- now also kept as a row, so the intern, the reviewer and the coordinators can read what happened in order. The note
-- is the reviewer's reason or the intern's objection; nothing here comes from a peer-mentoring report.
CREATE TABLE iskur_week_event (
    id                         UUID PRIMARY KEY,
    week_id                    UUID NOT NULL REFERENCES iskur_work_week (id) ON DELETE CASCADE,
    -- The order within the week, from 0: two steps can share a timestamp.
    step                       INTEGER NOT NULL,
    kind                       VARCHAR(16) NOT NULL,
    -- The intern for SUBMITTED and OBJECTION; the leader or coordinator for a decision.
    actor_id                   UUID REFERENCES app_user (id) ON DELETE SET NULL,
    note                       VARCHAR(500),
    check_time_matches_work    BOOLEAN,
    check_effort_documented    BOOLEAN,
    check_outputs_sufficient   BOOLEAN,
    created_at                 TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_iskur_week_event_step UNIQUE (week_id, step),
    CONSTRAINT chk_iskur_week_event_step CHECK (step >= 0),
    CONSTRAINT chk_iskur_week_event_kind CHECK (kind IN
        ('SUBMITTED', 'APPROVED', 'REVISION', 'REJECTED', 'OBJECTION')),
    CONSTRAINT chk_iskur_week_event_note CHECK (kind NOT IN ('REVISION', 'REJECTED', 'OBJECTION')
        OR (note IS NOT NULL AND btrim(note) <> '')),
    CONSTRAINT chk_iskur_week_event_submitted CHECK (kind <> 'SUBMITTED' OR note IS NULL)
);

CREATE INDEX ix_iskur_week_event_actor ON iskur_week_event (actor_id);

-- What the weeks hold today becomes their first history rows: the last send, its decision, the objection to it.
INSERT INTO iskur_week_event (id, week_id, step, kind, actor_id, created_at)
SELECT gen_random_uuid(), w.id, 0, 'SUBMITTED', w.user_id, w.submitted_at
FROM iskur_work_week w
WHERE w.submitted_at IS NOT NULL;

INSERT INTO iskur_week_event (id, week_id, step, kind, actor_id, note, check_time_matches_work,
    check_effort_documented, check_outputs_sufficient, created_at)
SELECT gen_random_uuid(), w.id, 1,
    CASE w.decision WHEN 'APPROVE' THEN 'APPROVED' WHEN 'REVISION' THEN 'REVISION' ELSE 'REJECTED' END,
    w.decided_by, w.decision_reason, w.check_time_matches_work, w.check_effort_documented,
    w.check_outputs_sufficient, w.decided_at
FROM iskur_work_week w
WHERE w.decision IS NOT NULL AND w.decided_at IS NOT NULL
    AND (w.decision = 'APPROVE' OR (w.decision_reason IS NOT NULL AND btrim(w.decision_reason) <> ''));

INSERT INTO iskur_week_event (id, week_id, step, kind, actor_id, note, created_at)
SELECT gen_random_uuid(), w.id, 2, 'OBJECTION', w.user_id, w.objection, w.objected_at
FROM iskur_work_week w
WHERE w.objection IS NOT NULL AND btrim(w.objection) <> '' AND w.objected_at IS NOT NULL;

-- 2. A support line reaches the leader as the pool's title and minutes only. The service never stores free text on
-- one; the database now refuses it too. Any stray text is cleared first so the constraint can be added.
UPDATE iskur_work_entry
SET output = NULL, note = NULL
WHERE kind = 'SUPPORT_ACTIVITY' AND (output IS NOT NULL OR note IS NOT NULL);

ALTER TABLE iskur_work_entry
    ADD CONSTRAINT chk_iskur_work_entry_support_text
    CHECK (kind <> 'SUPPORT_ACTIVITY' OR (output IS NULL AND note IS NULL));
