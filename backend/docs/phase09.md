# Phase 09: Notification Provider Abstraction and Email Integration

## Implementation Summary

Phase 09 implements a clean provider abstraction layer that decouples notification delivery from business logic. The system includes a mock provider for testing and a real SMTP provider for production email delivery, with normalized request/response models and comprehensive delivery tracking.

### Key Features
- Clean provider interface for extensibility
- Mock provider with deterministic test scenarios
- SMTP email provider for production use
- Normalized delivery request/response models
- Provider registry for dynamic provider selection
- Delivery attempt recording with detailed status tracking
- Distinction between provider acceptance vs. actual delivery
- Retryability classification for failed deliveries
- Tenant-isolated delivery records

## Files Modified/Created

### Provider Abstraction
- `src/main/java/com/pulserelay/provider/NotificationProvider.java` - Provider interface
- `src/main/java/com/pulserelay/provider/model/DeliveryRequest.java` - Normalized delivery request
- `src/main/java/com/pulserelay/provider/model/DeliveryResult.java` - Normalized delivery result

### Provider Implementations
- `src/main/java/com/pulserelay/provider/MockEmailProvider.java` - Mock email provider for testing
- `src/main/java/com/pulserelay/provider/SmtpEmailProvider.java` - SMTP email provider for production

### Provider Registry
- `src/main/java/com/pulserelay/provider/ProviderRegistry.java` - Dynamic provider selection

### Service Layer
- `src/main/java/com/pulserelay/service/DeliveryService.java` - Coordinates delivery execution

### Entities & Repositories
- `src/main/java/com/pulserelay/entity/DeliveryAttempt.java` - Delivery attempt record entity
- `src/main/java/com/pulserelay/repository/DeliveryAttemptRepository.java` - Data access for delivery attempts

### Controller & DTOs
- `src/main/java/com/pulserelay/controller/NotificationController.java` - Added `/test-delivery` POST endpoint
- `src/main/java/com/pulserelay/dto/TestDeliveryRequest.java` - Request DTO for test delivery endpoint
- `src/main/java/com/pulserelay/dto/TestDeliveryResponse.java` - Response DTO for test delivery endpoint

### Database
- `src/main/resources/db/migration/V005__Create_delivery_attempts_table.sql` - Migration for delivery_attempts table

### Configuration
- `src/main/resources/application.yml` - Provider configuration
- `pom.xml` - Added spring-boot-starter-mail dependency for SMTP provider

## Provider Abstraction Explanation

The provider abstraction layer ensures that business logic is completely decoupled from provider implementations. This enables:

1. **Easy Provider Switching** - Add new providers (SendGrid, Twilio, etc.) without modifying business logic
2. **Testability** - Mock provider simulates real-world scenarios without external dependencies
3. **Clear Contracts** - All providers implement the same interface with normalized request/response
4. **Provider Agnostic** - Business code never needs to know provider-specific details

### Provider Interface

All providers implement `NotificationProvider`:

```java
public interface NotificationProvider {
    String getChannel();           // What channel this provider handles
    String getName();              // Provider name
    DeliveryResult send(DeliveryRequest request);  // Send notification
    boolean isAvailable();         // Is provider configured
}
```

### Normalized Models

**DeliveryRequest** - Abstracted delivery details:
```java
- channel: EMAIL, SMS, PUSH, IN_APP
- recipient: Email, phone, device token, etc.
- subject: For EMAIL
- body: Main content
- metadata: Provider-specific options
```

**DeliveryResult** - Standardized response:
```java
- status: ACCEPTED, DELIVERED, FAILED, PERMANENT_FAILURE
- providerMessageId: ID from provider
- errorCode: Provider error code
- errorMessage: Human-readable error
- isRetryable: Whether to retry
```

### Status Distinctions

- **ACCEPTED**: Provider accepted the request (not delivery confirmation)
- **DELIVERED**: Message confirmed delivered to recipient
- **FAILED**: Delivery failed, potentially retryable
- **PERMANENT_FAILURE**: Non-retryable error

This distinction is critical: SMTP acceptance ≠ email delivery. We record ACCEPTED for SMTP because delivery confirmation would require webhook integration.

## Mock Provider Setup

The mock provider is useful for:
- Local development without real email setup
- Unit testing with deterministic behavior
- Testing failure scenarios

### Test Scenarios

The mock provider routes based on recipient email:

```
test-success@mock.local          → Status: DELIVERED
test-transient-failure@mock.local → Status: FAILED (isRetryable: true)
test-permanent-failure@mock.local → Status: PERMANENT_FAILURE (isRetryable: false)
<any other email>                → Status: DELIVERED
```

### Configuration

In `application.yml`:
```yaml
pulserelay:
  provider:
    email:
      type: mock  # Use mock provider
```

## SMTP Provider Configuration

For production email delivery, configure SMTP provider via environment variables:

```bash
export PULSERELAY_PROVIDER_EMAIL_TYPE=smtp
export PULSERELAY_SMTP_HOST=smtp.gmail.com
export PULSERELAY_SMTP_PORT=587
export PULSERELAY_SMTP_USERNAME=your-email@gmail.com
export PULSERELAY_SMTP_PASSWORD=your-app-password
export PULSERELAY_SMTP_FROM=noreply@example.com
export PULSERELAY_SMTP_AUTH=true
export PULSERELAY_SMTP_STARTTLS=true
```

Or in `application.yml`:
```yaml
spring:
  provider:
    email:
      type: smtp
  mail:
    host: smtp.gmail.com
    port: 587
    username: your-email@gmail.com
    password: your-app-password
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
```

**Never hardcode credentials in code or version control. Always use environment variables.**

## Delivery Attempt Records

Each delivery attempt creates a record with:

| Field | Purpose |
|-------|---------|
| `id` | Unique attempt ID |
| `notificationId` | Links to notification |
| `tenantId` | Tenant isolation |
| `channel` | EMAIL, SMS, PUSH, IN_APP |
| `provider` | MockEmail, Smtp, etc. |
| `recipient` | Email, phone, token |
| `status` | ACCEPTED, DELIVERED, FAILED, PERMANENT_FAILURE |
| `errorCode` | Provider error code |
| `errorMessage` | Human-readable error |
| `providerMessageId` | Message ID from provider |
| `isRetryable` | Whether to retry on failure |
| `attemptedAt` | Timestamp of attempt |

## Postman Testing Steps

### Prerequisites

Before testing, ensure a test tenant exists with an API key:

1. Execute the following SQL in pgAdmin to create a test tenant and API key:

```sql
-- Create test tenant
INSERT INTO tenants (id, name, email, status, created_at, updated_at)
VALUES (
    'd8f4e5c1-2b3a-4c5d-8e9f-1a2b3c4d5e6f'::uuid,
    'Test Tenant',
    'test@example.com',
    'ACTIVE',
    now(),
    now()
)
ON CONFLICT (email) DO NOTHING;

-- Create API key for test tenant
INSERT INTO api_keys (id, tenant_id, name, key_hash, status, created_at, updated_at)
VALUES (
    'a1b2c3d4-e5f6-4g7h-8i9j-0k1l2m3n4o5p'::uuid,
    'd8f4e5c1-2b3a-4c5d-8e9f-1a2b3c4d5e6f'::uuid,
    'Test API Key',
    'test-api-key-1',
    'ACTIVE',
    now(),
    now()
)
ON CONFLICT (key_hash) DO NOTHING;
```

2. Use API Key: `test-api-key-1` for all test requests

### Test 1: Mock Provider - Success Scenario

```
Method: POST
URL: http://localhost:8084/api/v1/notifications/test-delivery
Headers:
  X-API-KEY: test-api-key-1
  Content-Type: application/json
Body:
{
  "channel": "EMAIL",
  "recipient": "test-success@mock.local",
  "subject": "Test Email",
  "body": "This is a test email using mock provider"
}

Expected: 200 OK
Response includes:
  - status: "DELIVERED"
  - channel: "EMAIL"
  - provider: "MockEmail"
  - providerMessageId: "mock-<uuid>"
  - errorCode: null
  - errorMessage: null
  - isRetryable: false
```

### Test 2: Mock Provider - Transient Failure

```
Method: POST
URL: http://localhost:8084/api/v1/notifications/test-delivery
Headers:
  X-API-KEY: test-api-key-1
  Content-Type: application/json
Body:
{
  "channel": "EMAIL",
  "recipient": "test-transient-failure@mock.local",
  "subject": "Test Email",
  "body": "This will fail transiently"
}

Expected: 200 OK
Response includes:
  - status: "FAILED"
  - channel: "EMAIL"
  - provider: "MockEmail"
  - errorCode: "TRANSIENT_ERROR"
  - errorMessage: "Mock transient failure - service temporarily unavailable"
  - isRetryable: true
```

### Test 3: Mock Provider - Permanent Failure

```
Method: POST
URL: http://localhost:8084/api/v1/notifications/test-delivery
Headers:
  X-API-KEY: test-api-key-1
  Content-Type: application/json
Body:
{
  "channel": "EMAIL",
  "recipient": "test-permanent-failure@mock.local",
  "subject": "Test Email",
  "body": "This will fail permanently"
}

Expected: 200 OK
Response includes:
  - status: "PERMANENT_FAILURE"
  - channel: "EMAIL"
  - provider: "MockEmail"
  - errorCode: "INVALID_RECIPIENT"
  - errorMessage: "Mock permanent failure - invalid email address"
  - isRetryable: false
```

## Real Email Testing Steps (SMTP)

### Prerequisites

1. Configure SMTP in environment variables (see Configuration section)
2. Restart application with `PULSERELAY_PROVIDER_EMAIL_TYPE=smtp`

### Test: Send Real Email

```
Method: POST
URL: http://localhost:8084/api/v1/notifications/test-delivery
Headers:
  X-API-KEY: test-api-key-1
  Content-Type: application/json
Body:
{
  "channel": "EMAIL",
  "recipient": "your-email@example.com",
  "subject": "Test Email from PulseRelay",
  "body": "This is a real email from SMTP provider"
}

Expected: 200 OK
Response:
  - status: "ACCEPTED" (SMTP acceptance, not delivery confirmation)
  - channel: "EMAIL"
  - provider: "Smtp"
  - providerMessageId: "smtp-<uuid>"
```

Check your email inbox for the message.

## Expected Results

### Mock Provider Results

**Success**:
```json
{
  "id": "uuid",
  "status": "DELIVERED",
  "providerMessageId": "mock-uuid",
  "errorCode": null,
  "errorMessage": null,
  "isRetryable": false,
  "attemptedAt": "2026-11-14T18:30:00Z"
}
```

**Transient Failure**:
```json
{
  "id": "uuid",
  "status": "FAILED",
  "providerMessageId": null,
  "errorCode": "TRANSIENT_ERROR",
  "errorMessage": "Mock transient failure - service temporarily unavailable",
  "isRetryable": true,
  "attemptedAt": "2026-11-14T18:30:00Z"
}
```

**Permanent Failure**:
```json
{
  "id": "uuid",
  "status": "PERMANENT_FAILURE",
  "providerMessageId": null,
  "errorCode": "INVALID_RECIPIENT",
  "errorMessage": "Mock permanent failure - invalid email address",
  "isRetryable": false,
  "attemptedAt": "2026-11-14T18:30:00Z"
}
```

### SMTP Provider Results

**Successful Sending**:
```json
{
  "id": "uuid",
  "status": "ACCEPTED",
  "providerMessageId": "smtp-uuid",
  "errorCode": null,
  "errorMessage": null,
  "isRetryable": false,
  "attemptedAt": "2026-11-14T18:30:00Z"
}
```

**SMTP Configuration Error**:
```json
{
  "id": "uuid",
  "status": "PERMANENT_FAILURE",
  "providerMessageId": null,
  "errorCode": "PROVIDER_NOT_CONFIGURED",
  "errorMessage": "SMTP provider is not configured",
  "isRetryable": false,
  "attemptedAt": "2026-11-14T18:30:00Z"
}
```

## Status Accuracy

The system carefully distinguishes delivery states:

### ACCEPTED
- SMTP: Provider accepted the message (not delivered)
- SMS API: API responded positively
- PUSH: Provider accepted for queuing
- **Limitation**: Doesn't confirm recipient actually received it

### DELIVERED
- Email: Would require webhook from provider
- SMS: Delivery receipt from carrier (not implemented yet)
- PUSH: Device confirmed receipt (not implemented yet)
- **Note**: Phase 09 uses ACCEPTED for email; webhooks would be Phase 11+

### FAILED
- Transient network error
- Service temporarily unavailable
- Provider rate limited
- **Action**: Can be retried

### PERMANENT_FAILURE
- Invalid recipient address
- User rejected message
- Provider account issue
- **Action**: Do not retry

## Database Schema

```sql
CREATE TABLE delivery_attempts (
    id UUID PRIMARY KEY,
    notification_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    channel VARCHAR(50) NOT NULL,
    provider VARCHAR(100) NOT NULL,
    recipient VARCHAR(255),
    status VARCHAR(50) NOT NULL,
    error_code VARCHAR(100),
    error_message VARCHAR(500),
    provider_message_id VARCHAR(255),
    is_retryable BOOLEAN NOT NULL,
    attempted_at TIMESTAMP NOT NULL
);
```

### Indexes

- `idx_attempt_notification_id` - Fast lookup by notification
- `idx_attempt_tenant_id` - Tenant isolation queries
- `idx_attempt_status` - Filter by delivery status
- `idx_attempt_channel` - Filter by channel
- `idx_attempt_retryable` - Find failed retryable attempts

## Security Considerations

1. **Never hardcode credentials** - Use environment variables only
2. **Environment isolation** - SMTP credentials never logged or exposed
3. **Tenant isolation** - DeliveryAttempt records scoped by tenant_id
4. **Error messages** - Provider errors sanitized before logging
5. **Provider validation** - Credentials validated on provider initialization

## Architecture Diagram

```
NotificationController
        ↓
NotificationService (orchestration)
        ↓
RoutingPolicyService → TemplateService → DeliveryService
                                              ↓
                                        ProviderRegistry
                                              ↓
                    ┌─────────────────────────┼─────────────────────────┐
                    ↓                         ↓                         ↓
            MockEmailProvider         SmtpEmailProvider         (Future: SendGrid)
                    ↓                         ↓                         ↓
                DeliveryResult ← DeliveryResult ← DeliveryResult
                    ↓                         ↓                         ↓
                DeliveryAttemptRepository (persistent records)
```

## Implementation Notes

### Provider Registration

Providers are auto-registered via `ProviderRegistry`:
- Only available providers are registered
- Registry is injected into `DeliveryService`
- Provider selection is based on channel

### Error Handling

SMTP provider classifies errors:
- **Retryable**: Network errors, temporary service issues
- **Non-retryable**: Invalid email, user rejection, authentication failure

### Testing Strategy

1. **Unit tests**: Test each provider independently with mock dependencies
2. **Mock provider tests**: Verify deterministic behavior
3. **Integration tests**: Test end-to-end flow (notification → delivery)
4. **Postman tests**: Manual testing of APIs

## Constraints Satisfied

✅ No automatic retries (recorded for Phase 11+)
✅ No Redis (not needed yet)
✅ Provider credentials via environment variables only
✅ Delivery status reflects known state (ACCEPTED vs DELIVERED distinction)
✅ Mock provider for local testing
✅ Real SMTP provider for production
✅ Delivery attempts persisted to database
✅ Tenant isolation on all operations

## Future Enhancements (Phase 10+)

Not implemented in Phase 09 per requirements:

1. **SendGrid Provider** - Third-party email service integration
2. **SMS Provider** - Twilio or similar SMS delivery
3. **Push Provider** - Firebase Cloud Messaging
4. **Webhook Handlers** - Process delivery confirmations from providers
5. **Automatic Retries** - Retry failed transient deliveries
6. **Delivery Analytics** - Success rates, timing metrics
7. **Rate Limiting** - Provider quota management
8. **Provider Fallbacks** - Primary/backup provider switching

## Git Commit Message

```
feat(phase09): notification provider abstraction and email integration

- Add NotificationProvider interface for clean provider abstraction
- Implement MockEmailProvider with deterministic test scenarios
- Implement SmtpEmailProvider for production email delivery
- Create normalized DeliveryRequest and DeliveryResult models
- Add ProviderRegistry for dynamic provider selection
- Create DeliveryService coordinating delivery execution
- Add DeliveryAttempt entity for persistence of attempt records
- Create DeliveryAttemptRepository for data access
- Add V005 database migration for delivery_attempts table
- Configure SMTP via environment variables (no hardcoded secrets)
- Distinguish ACCEPTED (provider acceptance) vs DELIVERED (confirmed)
- Classify delivery failures as retryable or permanent
- Implement tenant isolation on delivery records
- Add mock provider with success/failure test scenarios
- Support SMTP provider with configurable credentials
- Record all delivery attempts with detailed status tracking
- Add provider availability checking and graceful error handling
```

