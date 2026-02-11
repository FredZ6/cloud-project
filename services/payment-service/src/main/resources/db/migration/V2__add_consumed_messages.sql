CREATE TABLE consumed_messages (
    id UUID PRIMARY KEY,
    message_id VARCHAR(128) NOT NULL,
    consumer VARCHAR(80) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_consumed_messages_message_consumer UNIQUE (message_id, consumer)
);
