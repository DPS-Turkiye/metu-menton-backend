-- Support requests between İŞKUR teams (student handbook p.12, p.14, p.18: "Başka ekipten destek lazım → Liderin
-- talep açar"). A team's leader asks another team of the program, optionally for one of their team's tasks; that
-- team's leader accepts or declines, with an optional note. The people are references only and go with an erased
-- account; the request stays with the two teams.

CREATE TABLE iskur_team_support_request (
    id            UUID PRIMARY KEY,
    program_id    UUID NOT NULL REFERENCES program (id) ON DELETE CASCADE,
    from_team_id  UUID NOT NULL REFERENCES iskur_team (id) ON DELETE CASCADE,
    to_team_id    UUID NOT NULL REFERENCES iskur_team (id) ON DELETE CASCADE,
    task_id       UUID REFERENCES iskur_task (id) ON DELETE SET NULL,
    opened_by     UUID REFERENCES app_user (id) ON DELETE SET NULL,
    need          VARCHAR(500) NOT NULL,
    status        VARCHAR(16) NOT NULL,
    answer_note   VARCHAR(500),
    answered_by   UUID REFERENCES app_user (id) ON DELETE SET NULL,
    answered_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_iskur_team_support_status CHECK (status IN ('OPEN', 'ACCEPTED', 'DECLINED')),
    CONSTRAINT chk_iskur_team_support_need CHECK (length(btrim(need)) > 0),
    CONSTRAINT chk_iskur_team_support_teams CHECK (from_team_id <> to_team_id)
);

CREATE INDEX ix_iskur_team_support_from ON iskur_team_support_request (from_team_id, created_at DESC);
CREATE INDEX ix_iskur_team_support_to ON iskur_team_support_request (to_team_id, created_at DESC);
CREATE INDEX ix_iskur_team_support_task ON iskur_team_support_request (task_id);
CREATE INDEX ix_iskur_team_support_opened_by ON iskur_team_support_request (opened_by);
CREATE INDEX ix_iskur_team_support_answered_by ON iskur_team_support_request (answered_by);
