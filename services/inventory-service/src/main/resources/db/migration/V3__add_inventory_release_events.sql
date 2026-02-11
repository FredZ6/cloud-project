CREATE TABLE inventory_release_events (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE,
    reservation_id UUID NOT NULL,
    reason VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_inventory_release_events_created_at ON inventory_release_events(created_at);
