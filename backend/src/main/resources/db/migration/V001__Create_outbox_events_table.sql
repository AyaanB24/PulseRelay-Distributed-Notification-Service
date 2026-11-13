-- Phase 05: Create Outbox Events Table with Transactional Outbox Pattern
-- This migration creates the outbox_events table for reliable event publishing
-- with retry support and exponential backoff.
--
-- The Transactional Outbox Pattern ensures:
-- 1. Atomicity: Notification and outbox event saved in same transaction
-- 2. Reliability: Events are persisted before Kafka publishing
-- 3. Retryability: Failed events can be retried with backoff
-- 4. Auditability: Full history of publishing attempts

CREATE TABLE IF NOT EXISTS outbox_events (
    id UUID PRIMARY KEY,
    
    -- Event type for routing and filtering (e.g., "notification.created")
    event_type VARCHAR(100) NOT NULL,
    
    -- Aggregate root ID (notification ID that triggered this event)
    aggregate_id UUID NOT NULL,
    
    -- Event payload as JSON containing: eventId, eventType, notificationId, tenantId, occurredAt, payload
    payload JSONB NOT NULL,
    
    -- Event status in publishing pipeline
    status VARCHAR(20) NOT NULL,
    
    -- Number of publication attempts
    retry_count INT NOT NULL DEFAULT 0,
    
    -- Timestamp of last publication attempt (NULL until first attempt)
    last_attempted_at TIMESTAMP WITH TIME ZONE,
    
    -- Timestamp when event was successfully published to Kafka
    published_at TIMESTAMP WITH TIME ZONE,
    
    -- Audit timestamps
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Check constraint: Only allowed statuses
    CONSTRAINT outbox_events_status_check 
        CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED', 'TERMINAL_FAILED'))
);

-- Indexes for common queries
CREATE INDEX IF NOT EXISTS idx_outbox_status ON outbox_events(status);
CREATE INDEX IF NOT EXISTS idx_outbox_created_at ON outbox_events(created_at);
CREATE INDEX IF NOT EXISTS idx_outbox_aggregate_id ON outbox_events(aggregate_id);
CREATE INDEX IF NOT EXISTS idx_outbox_status_created ON outbox_events(status, created_at);
CREATE INDEX IF NOT EXISTS idx_outbox_retryable ON outbox_events(status, retry_count, last_attempted_at)
    WHERE status IN ('PENDING', 'FAILED');

-- Comments for documentation
COMMENT ON TABLE outbox_events IS 'Transactional Outbox Pattern table for reliable event publishing with retry support';
COMMENT ON COLUMN outbox_events.status IS 'PENDING: Waiting to publish; PUBLISHED: Successfully published; FAILED: Publication failed but retryable; TERMINAL_FAILED: Max retries exhausted';
COMMENT ON COLUMN outbox_events.retry_count IS 'Number of publication attempts (incremented on each failure)';
COMMENT ON COLUMN outbox_events.last_attempted_at IS 'Timestamp of last publication attempt (used for exponential backoff)';
