-- Phase 09: Create delivery_attempts table for recording delivery attempts

CREATE TABLE delivery_attempts (
    id UUID PRIMARY KEY,
    notification_id UUID,  -- Nullable: test deliveries don't have a notification
    tenant_id UUID NOT NULL,
    channel VARCHAR(50) NOT NULL,
    provider VARCHAR(100) NOT NULL,
    recipient VARCHAR(255),
    status VARCHAR(50) NOT NULL,
    error_code VARCHAR(100),
    error_message VARCHAR(500),
    provider_message_id VARCHAR(255),
    is_retryable BOOLEAN NOT NULL DEFAULT FALSE,
    attempted_at TIMESTAMP NOT NULL,
    
    -- Foreign key only enforced if notification_id is not null
    CONSTRAINT fk_delivery_attempt_notification 
        FOREIGN KEY (notification_id) REFERENCES notifications(id) ON DELETE CASCADE
);

-- Create indexes for common query patterns
CREATE INDEX idx_attempt_notification_id ON delivery_attempts(notification_id);
CREATE INDEX idx_attempt_tenant_id ON delivery_attempts(tenant_id);
CREATE INDEX idx_attempt_status ON delivery_attempts(status);
CREATE INDEX idx_attempt_channel ON delivery_attempts(channel);
CREATE INDEX idx_attempt_retryable ON delivery_attempts(is_retryable, status);
