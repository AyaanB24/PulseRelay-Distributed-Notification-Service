-- Phase 06: Create Processed Events Table for Consumer Idempotency
-- This migration creates the processed_events table to track Kafka messages
-- that have been successfully processed by the consumer.
--
-- The Inbox Pattern (complement to Outbox Pattern):
-- - Outbox (Phase 05): Events waiting to be published to Kafka
-- - Inbox (Phase 06): Events that have been consumed and processed
--
-- Deduplication Guarantee:
-- 1. Consumer receives message from Kafka
-- 2. Checks if event_id exists in this table
-- 3. If exists: Skip processing (duplicate)
-- 4. If not exists: Process notification, then insert record
-- 5. Database constraint ensures event_id is unique
--
-- This ensures Kafka redelivery doesn't cause duplicate business operations.

CREATE TABLE IF NOT EXISTS processed_events (
    id UUID PRIMARY KEY,
    
    -- Event ID from the outbox (globally unique, used for deduplication)
    event_id UUID NOT NULL UNIQUE,
    
    -- Aggregate root ID (notification ID) that was processed
    aggregate_id UUID NOT NULL,
    
    -- Event type (e.g., "notification.created")
    event_type VARCHAR(100) NOT NULL,
    
    -- Kafka metadata (for debugging and recovery)
    kafka_partition INT,
    kafka_offset BIGINT,
    
    -- When this event was processed by the consumer
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    
    -- When this record was created
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Index for efficient deduplication lookups
CREATE INDEX IF NOT EXISTS idx_processed_event_id ON processed_events(event_id);

-- Index for tracing which notifications were processed
CREATE INDEX IF NOT EXISTS idx_processed_aggregate_id ON processed_events(aggregate_id);

-- Index for monitoring processing latency
CREATE INDEX IF NOT EXISTS idx_processed_timestamp ON processed_events(processed_at);

-- Unique constraint ensures same event is only recorded once
CREATE UNIQUE INDEX IF NOT EXISTS idx_processed_unique_event ON processed_events(event_id);

-- Comments for documentation
COMMENT ON TABLE processed_events IS 'Inbox Pattern table for tracking processed Kafka events and preventing duplicate processing';
COMMENT ON COLUMN processed_events.event_id IS 'Unique event identifier (from outbox). Used for deduplication.';
COMMENT ON COLUMN processed_events.kafka_partition IS 'Kafka partition this message was consumed from';
COMMENT ON COLUMN processed_events.kafka_offset IS 'Kafka offset of this message (unique within partition)';
