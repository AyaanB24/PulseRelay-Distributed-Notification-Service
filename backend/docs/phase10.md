# Phase 10: Reliable Delivery Retries and Attempt Tracking

## Implementation Summary

Phase 10 implements automatic retry handling for transient provider failures with exponential backoff. The system distinguishes between retryable transient failures (timeouts, rate limits, temporary errors) and permanent failures (invalid recipients, unsubscribed users) that should not be retried indefinitely.

### Key Features
- Exponential backoff retry scheduling
- Configurable max attempts, initial delay, and backoff multiplier
- Failure classification (transient vs permanent)
- Persistent retry state survives application restart
- Scheduled retry execution without blocking Kafka consumers
- Complete attempt history tracking
- Status transitions: ACCEPTED → RETRY_SCHEDULED → ACCEPTED → DELIVERED

## Files Modified/Created

### Configuration
- `src/main/java/com/pulserelay/config/RetryPolicyConfig.java` - Retry policy configuration
- `src/main/resources/application.yml` - Retry policy settings

### Services
- `src/main/java/com/pulserelay/service/RetryScheduler.java` - Retry scheduling with backoff
- `src/main/java/com/pulserelay/service/RetryExecutor.java` - Scheduled retry execution
- `src/main/java/com/pulserelay/service/DeliveryService.java` - Updated to integrate retry logic

### Entities & Repositories
- `src/main/java/com/pulserelay/entity/DeliveryAttempt.java` - Added nextAttemptAt field
- `src/main/java/com/pulserelay/repository/DeliveryAttemptRepository.java` - Added retry queries

### Database
- `src/main/resources/db/migration/V006__Add_retry_support_to_delivery_attempts.sql` - Retry persistence

## Retry Architecture

### Exponential Backoff Formula

```
delay = min(initialDelay * (multiplier ^ attemptNumber), maxDelay)

Example (initialDelay=1s, multiplier=2, maxDelay=60s):
- Attempt 0 (initial):    immediate
- Attempt 1 (retry 1):    1 second
- Attempt 2 (retry 2):    2 seconds
- Attempt 3 (retry 3):    4 seconds
- Attempt 4 (retry 4):    8 seconds
- Attempt 5+ (retry 5+):  min(16s, 60s) = 16 seconds
```

### Failure Classification

**TRANSIENT Failures (Retryable)**
- Network timeouts
- Provider rate limits (429, 503)
- Temporary service unavailable (5xx)
- Connection reset
- Read/write timeouts

**PERMANENT Failures (Non-Retryable)**
- Invalid email address
- User unsubscribed
- Invalid phone number
- Invalid push token
- Account suspended
- Authentication failure
- Invalid recipient (4xx errors from provider)

### Status Flow

```
Initial Attempt
     ↓
Provider Response
     ↓
    ├─ DELIVERED → Done (success)
    ├─ PERMANENT_FAILURE → Done (no retry)
    └─ FAILED + isRetryable=true
         ↓
    Schedule Retry (RETRY_SCHEDULED)
         ↓
    Wait until nextAttemptAt
         ↓
    Execute Retry
         ↓
    Repeat until DELIVERED or maxAttempts exceeded
```

## Configuration

### application.yml

```yaml
pulserelay:
  retry:
    policy:
      max-attempts: 5              # Total attempts (1 initial + 4 retries)
      initial-delay-ms: 1000       # First retry delay: 1 second
      max-delay-ms: 60000          # Cap delay at 60 seconds
      backoff-multiplier: 2.0      # Double delay each retry
```

### Environment Variables

Set via environment (no environment variable overrides for retry - use application.yml):

```bash
# Optional: override application.yml values
export PULSERELAY_RETRY_POLICY_MAX_ATTEMPTS=5
export PULSERELAY_RETRY_POLICY_INITIAL_DELAY_MS=1000
export PULSERELAY_RETRY_POLICY_MAX_DELAY_MS=60000
export PULSERELAY_RETRY_POLICY_BACKOFF_MULTIPLIER=2.0
```

## DeliveryAttempt Schema

The `delivery_attempts` table now includes:

```sql
-- New Phase 10 fields
next_attempt_at TIMESTAMP NULL  -- When to retry (null if not scheduled)

-- New enum value
status: RETRY_SCHEDULED  -- Delivery scheduled for retry
```

### Status Values
- **ACCEPTED**: Provider accepted (not delivery confirmation)
- **DELIVERED**: Message confirmed delivered
- **FAILED**: Delivery failed, potentially retryable
- **PERMANENT_FAILURE**: Non-retryable failure
- **RETRY_SCHEDULED**: Scheduled for automatic retry

## Retry Execution

### Scheduled Retry Task

```java
// Runs every 30 seconds (configurable)
// Finds all RETRY_SCHEDULED attempts where nextAttemptAt <= now()
// Executes delivery again for each
// Records new attempt record
```

### Automatic Behavior

1. Initial delivery fails with `FAILED` status and `isRetryable=true`
2. System schedules retry with `RETRY_SCHEDULED` status
3. `nextAttemptAt` is set to future time based on exponential backoff
4. RetryExecutor checks every 30 seconds for due retries
5. When due, retry is executed (goes through provider again)
6. New attempt record created for the retry
7. Success → status becomes DELIVERED
8. Failure → scheduled for next retry (if under max attempts)

## Postman Testing Steps

### Prerequisites

See Phase 09 for SMTP setup. Make sure both Mock and SMTP providers can be switched via `pulserelay.provider.email.type` in application.yml.

### Test 1: Transient Failure Retry Sequence

**Step 1: Send notification with transient failure**

```
POST http://localhost:8084/api/v1/notifications/test-delivery

Headers:
X-API-KEY: test-api-key-1
Content-Type: application/json

Body:
{
  "channel": "EMAIL",
  "recipient": "test-transient-failure@mock.local",
  "subject": "Transient Failure Test",
  "body": "This will fail with retryable error"
}
```

**Response (200 OK):**
```json
{
  "id": "attempt-uuid-1",
  "status": "RETRY_SCHEDULED",
  "isRetryable": true,
  "nextAttemptAt": "2026-11-15T00:00:01.000+00:00",
  "errorCode": "TRANSIENT_ERROR"
}
```

**Step 2: Query database to see retry scheduled**

```sql
SELECT id, status, next_attempt_at, attempt_number, error_code 
FROM delivery_attempts 
WHERE id = 'attempt-uuid-1';
```

**Step 3: Wait for retry (or trigger manually)**

Wait 1-2 seconds or query for due retries:

```sql
SELECT * FROM delivery_attempts 
WHERE status = 'RETRY_SCHEDULED' 
AND next_attempt_at <= NOW();
```

**Step 4: Monitor logs**

```
[INFO] Checking for scheduled retries
[INFO] Found 1 delivery attempts due for retry
[INFO] Executing retry for notification abc-123
[INFO] Retry attempt def-456 completed with status: DELIVERED
```

### Test 2: Permanent Failure (No Retry)

```
POST http://localhost:8084/api/v1/notifications/test-delivery

Body:
{
  "channel": "EMAIL",
  "recipient": "test-permanent-failure@mock.local",
  "subject": "Permanent Failure Test",
  "body": "This will fail permanently"
}
```

**Response (200 OK):**
```json
{
  "status": "PERMANENT_FAILURE",
  "isRetryable": false,
  "nextAttemptAt": null,
  "errorCode": "INVALID_RECIPIENT"
}
```

Note: No `nextAttemptAt` - this will NOT be retried.

### Test 3: Success After Retry

**Step 1: Send test that will eventually succeed (mock provider always succeeds on retry)**

```
{
  "channel": "EMAIL",
  "recipient": "test-success@mock.local",
  "subject": "Success Test",
  "body": "This will succeed"
}
```

**Step 2: View attempt history**

```sql
SELECT id, status, attempt_number, error_code, next_attempt_at, attempted_at
FROM delivery_attempts 
WHERE notification_id = 'notif-uuid'
ORDER BY attempted_at ASC;
```

Output shows delivery flow:
```
attempt-1 | ACCEPTED           | 1 | null          | null              | 2026-11-15 00:00:00
attempt-2 | DELIVERED          | 2 | null          | null              | 2026-11-15 00:00:02
```

## Simulating Failures

### Mock Provider Scenarios

The mock provider routes based on recipient email:

| Recipient | Behavior | isRetryable | Result |
|-----------|----------|-------------|--------|
| `test-success@mock.local` | Succeeds immediately | N/A | DELIVERED |
| `test-transient-failure@mock.local` | Fails with transient error | true | FAILED → RETRY_SCHEDULED |
| `test-permanent-failure@mock.local` | Fails with permanent error | false | PERMANENT_FAILURE |
| Any other email | Succeeds | N/A | DELIVERED |

### SMTP Provider Scenarios

For real email testing, use these errors:

| Scenario | How to Simulate | isRetryable |
|----------|-----------------|-------------|
| Network timeout | Firewall block SMTP port | true |
| Invalid recipient | Use malformed email | false |
| Auth failure | Wrong password | false |
| Rate limit | Send many emails quickly | true |

## Expected Attempt History

### Scenario: Transient Failure with 3 Retries Until Success

```
┌─────────────────────────────────────────────┐
│ Notification ID: abc-def-ghi-jkl           │
└─────────────────────────────────────────────┘

Attempt 1 (t=0s)
  Status: FAILED
  Error: TRANSIENT_ERROR
  isRetryable: true
  → Schedule retry at t=1s
  nextAttemptAt: 2026-11-15T00:00:01Z

Attempt 2 (t=1s)
  Status: FAILED
  Error: TRANSIENT_ERROR
  isRetryable: true
  → Schedule retry at t=3s (1s + 2s backoff)
  nextAttemptAt: 2026-11-15T00:00:03Z

Attempt 3 (t=3s)
  Status: FAILED
  Error: TRANSIENT_ERROR
  isRetryable: true
  → Schedule retry at t=7s (3s + 4s backoff)
  nextAttemptAt: 2026-11-15T00:00:07Z

Attempt 4 (t=7s)
  Status: DELIVERED
  → Success! No more retries
  nextAttemptAt: null
```

## Database Queries

### Find All Retryable Failures

```sql
SELECT * FROM delivery_attempts
WHERE status = 'FAILED' AND is_retryable = true
ORDER BY attempted_at DESC;
```

### Find Retries Scheduled for Next Hour

```sql
SELECT * FROM delivery_attempts
WHERE status = 'RETRY_SCHEDULED'
AND next_attempt_at BETWEEN NOW() AND NOW() + INTERVAL '1 hour'
ORDER BY next_attempt_at ASC;
```

### View Attempt History for a Notification

```sql
SELECT 
  id,
  status,
  error_code,
  provider_message_id,
  is_retryable,
  next_attempt_at,
  attempted_at
FROM delivery_attempts
WHERE notification_id = 'your-notification-id'
ORDER BY attempted_at ASC;
```

### Count Retries by Status

```sql
SELECT status, COUNT(*) as count
FROM delivery_attempts
WHERE notification_id = 'your-notification-id'
GROUP BY status;
```

## Constraints Satisfied

✅ Transient failures are retried
✅ Permanent failures are not retried indefinitely
✅ Backoff increases exponentially between attempts
✅ Attempt history persisted to database
✅ Retry state survives application restart
✅ No blocking of Kafka consumers (background scheduled task)
✅ Maximum attempts enforced
✅ Configurable retry policy
✅ Failure classification in provider responses
✅ Each attempt recorded with full context

## Important Notes

### Exactly-Once Delivery

This implementation does NOT guarantee exactly-once delivery. Residual duplicate-delivery risk exists:

- If the provider accepts a message but the app crashes before recording the attempt, the message may be retried and delivered twice
- Document this in client agreements
- Implement idempotency in message processing (Phase 11+)

### Retry Scheduling

- Retries are checked every 30 seconds (configurable)
- There is a maximum 30-second delay between "due" retry and execution
- For critical applications, reduce the check interval in `RetryExecutor.java`

### Application Restart

- On restart, the application will resume scheduled retries from the database
- Retries that became "due" while app was down execute immediately
- No retries are lost

## Future Enhancements (Phase 11+)

Not implemented in Phase 10:

1. **Manual Retry API** - Admin endpoint to manually retry failed attempts
2. **Webhook Confirmation** - Listen for provider delivery receipts
3. **Dead Letter Queue** - Move permanent failures after max attempts
4. **Retry Analytics** - Metrics on retry success rates
5. **Circuit Breaker** - Pause retries if provider is consistently down
6. **Provider Idempotency** - Use provider's message deduplication keys
7. **Concurrent Retry Limits** - Throttle retry concurrency

## Git Commit Message

```
feat(phase10): reliable delivery retries with exponential backoff

- Implement exponential backoff retry scheduling
- Add RetryScheduler service with configurable policy
- Add RetryExecutor scheduled task for due retries
- Classify failures as transient (retryable) vs permanent
- Add RETRY_SCHEDULED status to DeliveryAttempt
- Add nextAttemptAt field for retry persistence
- Create V006 migration for retry tracking
- Add retry query methods to DeliveryAttemptRepository
- Integrate retry logic into DeliveryService
- Support max attempts, initial delay, backoff multiplier
- Retry state persists across application restarts
- Scheduled retries execute every 30 seconds
- Document failure classification and retry behavior
- Distinguish between provider acceptance and delivery confirmation
- Note: Does not guarantee exactly-once delivery (residual risk documented)
```
