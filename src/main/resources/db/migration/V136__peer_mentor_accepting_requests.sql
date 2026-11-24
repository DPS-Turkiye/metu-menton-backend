-- Figma 7.7 "Yeni talep almaya açığım": a peer mentor may pause new requests without changing their capacity.
ALTER TABLE mentor_profile ADD COLUMN accepting_requests BOOLEAN NOT NULL DEFAULT TRUE;
