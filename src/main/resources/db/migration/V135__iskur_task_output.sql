-- İŞKUR task outputs: the files an intern hands in with a team task (at most five per task, kept by the service). Each
-- is a stored_document of kind iskur-task-output, uploaded through DocumentService; this table says which task it
-- belongs to. The task's team leaders and the program's coordinators read them. It holds work, never a mentee, a
-- meeting note or a report body.
CREATE TABLE iskur_task_output (
    id           UUID PRIMARY KEY,
    -- A deleted task takes its links with it; the service removes the files first.
    task_id      UUID NOT NULL REFERENCES iskur_task (id) ON DELETE CASCADE,
    document_id  UUID NOT NULL REFERENCES stored_document (id) ON DELETE CASCADE,
    uploaded_by  UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_iskur_task_output_document UNIQUE (document_id)
);

CREATE INDEX ix_iskur_task_output_task ON iskur_task_output (task_id, created_at);
CREATE INDEX ix_iskur_task_output_uploaded_by ON iskur_task_output (uploaded_by);
