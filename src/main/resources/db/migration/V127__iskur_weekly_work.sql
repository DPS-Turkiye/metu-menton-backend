-- İŞKUR weekly work (Figma 284:2: 2.1 Ekibim, 3.1/3.2 Çalışma kaydı, 4.1 leader review; rules frame 322:3413). A team
-- leader assigns tasks to their sub-group; each intern keeps one weekly log of team work and mentoring support
-- activities, sends it to their leader, and the leader decides. The mentoring meetings themselves stay in the peer
-- report tables: this schema never copies a mentee, a note or a report body, so a leader cannot read one from here.

-- The support activities an intern picks in a week without a peer meeting, kept per program by its coordinators.
CREATE TABLE iskur_support_activity (
    id           UUID PRIMARY KEY,
    program_id   UUID NOT NULL REFERENCES program (id) ON DELETE CASCADE,
    title        VARCHAR(120) NOT NULL,
    description  VARCHAR(500),
    -- A retired activity stays for the weeks that already used it, but is no longer offered.
    active       BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order   INTEGER NOT NULL DEFAULT 0,
    created_by   UUID REFERENCES app_user (id) ON DELETE SET NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_iskur_support_activity_title UNIQUE (program_id, title),
    CONSTRAINT chk_iskur_support_activity_title CHECK (btrim(title) <> '')
);

CREATE INDEX ix_iskur_support_activity_created_by ON iskur_support_activity (created_by);

-- A task names its team and the team's program, so the pair must agree; this key lets iskur_task say so.
ALTER TABLE iskur_team ADD CONSTRAINT uq_iskur_team_id_program UNIQUE (id, program_id);

-- A team task (2.1 "Görev ata"). Estimates are in half-hour steps from one hour to one work day; the actual time may
-- run over, up to a three-day week. A team goes only once it is empty, so its tasks go with it; the log entries that
-- used a task keep their own title and minutes.
CREATE TABLE iskur_task (
    id                 UUID PRIMARY KEY,
    program_id         UUID NOT NULL REFERENCES program (id) ON DELETE CASCADE,
    team_id            UUID NOT NULL,
    assignee_id        UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    created_by         UUID REFERENCES app_user (id) ON DELETE SET NULL,
    title              VARCHAR(200) NOT NULL,
    kind               VARCHAR(24) NOT NULL,
    estimated_minutes  INTEGER NOT NULL,
    due_date           DATE NOT NULL,
    needs_output       BOOLEAN NOT NULL,
    description        VARCHAR(1000),
    status             VARCHAR(16) NOT NULL,
    actual_minutes     INTEGER,
    -- The output is a link and a short note in v1; file uploads come with ODTÜ's file storage.
    output_link        VARCHAR(500),
    output_note        VARCHAR(500),
    -- Why the task moves to next week ("sonraki haftaya devretti").
    handover_reason    VARCHAR(500),
    completed_at       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_iskur_task_team FOREIGN KEY (team_id, program_id) REFERENCES iskur_team (id, program_id)
        ON DELETE CASCADE,
    CONSTRAINT chk_iskur_task_title CHECK (btrim(title) <> ''),
    CONSTRAINT chk_iskur_task_kind CHECK (kind IN ('TEAM_TASK', 'EVENT', 'CORPORATE', 'CONTRIBUTION_PROJECT')),
    CONSTRAINT chk_iskur_task_status CHECK (status IN ('OPEN', 'DONE', 'HANDED_OVER')),
    CONSTRAINT chk_iskur_task_estimate CHECK (estimated_minutes BETWEEN 60 AND 450 AND estimated_minutes % 30 = 0),
    CONSTRAINT chk_iskur_task_actual CHECK (actual_minutes IS NULL OR actual_minutes BETWEEN 1 AND 1350),
    CONSTRAINT chk_iskur_task_done CHECK ((status = 'DONE') = (completed_at IS NOT NULL)),
    CONSTRAINT chk_iskur_task_done_time CHECK (status <> 'DONE' OR actual_minutes IS NOT NULL),
    CONSTRAINT chk_iskur_task_handover CHECK (status <> 'HANDED_OVER'
        OR (handover_reason IS NOT NULL AND btrim(handover_reason) <> ''))
);

CREATE INDEX ix_iskur_task_team_due ON iskur_task (team_id, due_date);
CREATE INDEX ix_iskur_task_assignee_due ON iskur_task (assignee_id, due_date);
CREATE INDEX ix_iskur_task_program ON iskur_task (program_id);
CREATE INDEX ix_iskur_task_created_by ON iskur_task (created_by);

-- One intern's week (Monday to Sunday): drafted, sent to their leader, then approved, sent back or rejected. The
-- status and the leader's decision move together. The reason is required for a revision or a rejection, and the
-- intern may answer either once with an objection.
CREATE TABLE iskur_work_week (
    id                         UUID PRIMARY KEY,
    program_id                 UUID NOT NULL REFERENCES program (id) ON DELETE CASCADE,
    user_id                    UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    week_start                 DATE NOT NULL,
    status                     VARCHAR(24) NOT NULL,
    submitted_at               TIMESTAMPTZ,
    decision                   VARCHAR(16),
    decision_reason            VARCHAR(500),
    check_time_matches_work    BOOLEAN,
    check_effort_documented    BOOLEAN,
    check_outputs_sufficient   BOOLEAN,
    decided_by                 UUID REFERENCES app_user (id) ON DELETE SET NULL,
    decided_at                 TIMESTAMPTZ,
    objection                  VARCHAR(500),
    objected_at                TIMESTAMPTZ,
    created_at                 TIMESTAMPTZ NOT NULL,
    updated_at                 TIMESTAMPTZ NOT NULL,
    version                    BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_iskur_work_week UNIQUE (program_id, user_id, week_start),
    CONSTRAINT chk_iskur_work_week_monday CHECK (EXTRACT(ISODOW FROM week_start) = 1),
    CONSTRAINT chk_iskur_work_week_status CHECK (status IN
        ('DRAFT', 'SUBMITTED', 'APPROVED', 'REVISION_REQUESTED', 'REJECTED')),
    CONSTRAINT chk_iskur_work_week_decision CHECK (decision IS NULL OR decision IN ('APPROVE', 'REVISION', 'REJECT')),
    CONSTRAINT chk_iskur_work_week_decided CHECK ((decision IS NULL) = (decided_at IS NULL)),
    CONSTRAINT chk_iskur_work_week_submitted CHECK (status = 'DRAFT' OR submitted_at IS NOT NULL),
    CONSTRAINT chk_iskur_work_week_approved CHECK ((status = 'APPROVED') = (COALESCE(decision, '') = 'APPROVE')),
    CONSTRAINT chk_iskur_work_week_revision CHECK
        ((status = 'REVISION_REQUESTED') = (COALESCE(decision, '') = 'REVISION')),
    CONSTRAINT chk_iskur_work_week_rejected CHECK ((status = 'REJECTED') = (COALESCE(decision, '') = 'REJECT')),
    CONSTRAINT chk_iskur_work_week_reason CHECK (decision IS NULL OR decision = 'APPROVE'
        OR (decision_reason IS NOT NULL AND btrim(decision_reason) <> '')),
    CONSTRAINT chk_iskur_work_week_objection CHECK ((objection IS NULL) = (objected_at IS NULL)),
    CONSTRAINT chk_iskur_work_week_objection_on CHECK (objection IS NULL OR decision IN ('REVISION', 'REJECT'))
);

CREATE INDEX ix_iskur_work_week_user ON iskur_work_week (user_id, week_start);
CREATE INDEX ix_iskur_work_week_program_week ON iskur_work_week (program_id, week_start);
CREATE INDEX ix_iskur_work_week_decided_by ON iskur_work_week (decided_by);

-- One line of the weekly log: team work (optionally on a task) or a mentoring support activity from the pool. Both
-- keep their own title, so a deleted task or a renamed activity does not change a week that was already sent. A
-- draft's lines are replaced as a whole on save, so a line has no updated_at.
CREATE TABLE iskur_work_entry (
    id                   UUID PRIMARY KEY,
    week_id              UUID NOT NULL REFERENCES iskur_work_week (id) ON DELETE CASCADE,
    entry_date           DATE NOT NULL,
    kind                 VARCHAR(24) NOT NULL,
    task_id              UUID REFERENCES iskur_task (id) ON DELETE SET NULL,
    support_activity_id  UUID REFERENCES iskur_support_activity (id) ON DELETE SET NULL,
    title                VARCHAR(200) NOT NULL,
    minutes              INTEGER NOT NULL,
    output               VARCHAR(500),
    note                 VARCHAR(500),
    sort_order           INTEGER NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_iskur_work_entry_title CHECK (btrim(title) <> ''),
    CONSTRAINT chk_iskur_work_entry_kind CHECK (kind IN ('TEAM', 'SUPPORT_ACTIVITY')),
    CONSTRAINT chk_iskur_work_entry_minutes CHECK (minutes BETWEEN 1 AND 450),
    CONSTRAINT chk_iskur_work_entry_task CHECK (kind = 'TEAM' OR task_id IS NULL),
    CONSTRAINT chk_iskur_work_entry_activity CHECK (kind = 'SUPPORT_ACTIVITY' OR support_activity_id IS NULL)
);

CREATE INDEX ix_iskur_work_entry_week ON iskur_work_entry (week_id, entry_date, sort_order);
CREATE INDEX ix_iskur_work_entry_task ON iskur_work_entry (task_id);
CREATE INDEX ix_iskur_work_entry_activity ON iskur_work_entry (support_activity_id);

-- "Two weeks in a row without a peer meeting" is told to the program's coordinators once per intern and stretch: the
-- Monday job writes the second empty week here, so a re-run or a second instance does not notify twice.
CREATE TABLE iskur_meeting_gap_alert (
    id          UUID PRIMARY KEY,
    program_id  UUID NOT NULL REFERENCES program (id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    week_start  DATE NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_iskur_meeting_gap_alert UNIQUE (program_id, user_id, week_start)
);

CREATE INDEX ix_iskur_meeting_gap_alert_user ON iskur_meeting_gap_alert (user_id);
