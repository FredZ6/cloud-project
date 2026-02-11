CREATE TABLE inventory_reservations (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL,
    reason VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE inventory_reservation_items (
    id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL REFERENCES inventory_reservations(id) ON DELETE CASCADE,
    sku_id VARCHAR(64) NOT NULL,
    quantity INTEGER NOT NULL
);

CREATE INDEX idx_inventory_reservation_items_reservation_id ON inventory_reservation_items(reservation_id);

CREATE TABLE consumed_messages (
    id UUID PRIMARY KEY,
    message_id VARCHAR(128) NOT NULL,
    consumer VARCHAR(80) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_consumed_messages_message_consumer UNIQUE (message_id, consumer)
);
