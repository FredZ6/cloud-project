-- Supports keyset/cursor pagination ordering: created_at DESC, id DESC
CREATE INDEX IF NOT EXISTS idx_inventory_release_events_created_at_id_desc
    ON inventory_release_events (created_at DESC, id DESC);

