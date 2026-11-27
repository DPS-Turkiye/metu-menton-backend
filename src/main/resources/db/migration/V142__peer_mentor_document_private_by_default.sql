-- Peer mentors now choose, file by file, whether the students of their program see a document on their profile, and
-- every file starts hidden. Files shown before this choice existed (the CV card used to start as shown) go back to
-- VERIFICATION_ONLY; the mentor can switch each one on again. Portfolio links are not files and keep their setting.
UPDATE peer_mentor_document
SET visibility = 'VERIFICATION_ONLY'
WHERE document_id IS NOT NULL
  AND visibility = 'PROFILE';

-- A transcript is only ever for the coordinators who verify it.
ALTER TABLE peer_mentor_document
    ADD CONSTRAINT chk_peer_mentor_document_transcript_private
        CHECK (evidence_type <> 'TRANSCRIPT' OR visibility = 'VERIFICATION_ONLY');
