-- Phase 10: Add retry support to delivery_attempts table

-- Add nextAttemptAt column for retry scheduling
ALTER TABLE delivery_attempts 
ADD COLUMN next_attempt_at TIMESTAMP NULL;

-- Create index for finding due retries (without function in WHERE clause)
CREATE INDEX idx_attempt_next_retry ON delivery_attempts(next_attempt_at) 
WHERE next_attempt_at IS NOT NULL;

-- Create index for finding retryable failed attempts
CREATE INDEX idx_attempt_retryable_failed ON delivery_attempts(status, is_retryable, attempted_at)
WHERE status = 'FAILED' AND is_retryable = true;
