# Phase 04 — Notification API and Persistence

## Overview

Phase 04 implements the core notification acceptance API, allowing tenant applications to submit notification requests to PulseRelay. Notifications are validated, persisted to PostgreSQL, and made available for future processing stages.

**Key Achievement**: Tenants can now submit notifications with guaranteed deduplication via idempotency keys.

---

## Files Created

### Entities
- `src/main/java/com/pulserelay/entity/Notification.java` — Notification entity with tenant relationship, channels, priority, payload, and status tracking

### DTOs
- `src/main/java/com/pulserelay/dto/NotificationRequest.java` — Validated request DTO with Bean Validation
- `src/main/java/com/pulserelay/dto/NotificationResponse.java` — Response DTO for 202 Accepted responses

### Services
- `src/main/java/com/pulserelay/service/NotificationService.java` — Business logic for validation, persistence, and idempotency handling

### Controllers
- `src/main/java/com/pulserelay/controller/NotificationController.java` — REST endpoint POST /api/v1/notifications

### Repositories
- `src/main/java/com/pulserelay/repository/NotificationRepository.java` — Database access with idempotency queries

### Exceptions
- `src/main/java/com/pulserelay/exception/NotificationException.java` — Custom exception for notification validation/persistence errors

### Configuration
- `src/main/java/com/pulserelay/config/GlobalExceptionHandler.java` — Centralized exception handling and error response formatting

---

## Notification Entity

The `Notification` entity represents a notification request submitted by a tenant. Each notification is tenant-scoped and immutable after creation.

### Fields

| Field | Type | Constraints | Description |
|-------|------|-----------|-------------|
| `id` | UUID | PRIMARY KEY | Unique notification identifier |
| `tenant` | Tenant | FOREIGN KEY (NOT NULL) | Tenant that owns this notification |
| `eventType` | String | NOT NULL, 100 chars | Event classification (e.g., order_confirmation) |
| `recipientEmail` | String | Optional, 255 chars | Recipient email address |
| `recipientPhone` | String | Optional, 20 chars | Recipient phone number |
| `recipientUserId` | String | Optional, 255 chars | Tenant's internal user ID |
| `requestedChannels` | String | NOT NULL, 255 chars | Comma-separated channels (EMAIL,SMS,PUSH,IN_APP) |
| `priority` | Enum | NOT NULL | CRITICAL, HIGH, NORMAL, LOW |
| `payload` | JSON | Optional | Template variables and metadata |
| `status` | Enum | NOT NULL | PENDING, PROCESSING, SENT, FAILED, EXPIRED |
| `idempotencyKey` | String | NOT NULL, 255 chars | Deduplication key (tenant-scoped) |
| `createdAt` | LocalDateTime | NOT NULL, immutable | Timestamp of creation |
| `updatedAt` | LocalDateTime | NOT NULL | Last modification timestamp |

### Indexes

```sql
-- Tenant lookup (fast filtering by tenant)
CREATE INDEX idx_tenant_id ON notifications(tenant_id);

-- Status filtering (for processing pipelines)
CREATE INDEX idx_status ON notifications(status);

-- Time-based queries (for retention policies)
CREATE INDEX idx_created_at ON notifications(created_at);

-- Idempotency enforcement (unique per tenant)
CREATE UNIQUE INDEX idx_tenant_idempotency ON notifications(tenant_id, idempotency_key);
```

---

## Notification API

### POST /api/v1/notifications

Submit a notification request for processing.

**Authentication**: Requires valid `X-API-KEY` header (tenant API key)

**Headers**:
```
X-API-KEY: pr_live_xxxxxxxxxxxxxxxxxxxxxx
Idempotency-Key: order-12345-2026-11-05
Content-Type: application/json
```

**Request Body**:
```json
{
  "eventType": "order_confirmation",
  "recipientEmail": "customer@example.com",
  "recipientPhone": "+1-555-0123",
  "recipientUserId": "user_5f7c8d9e",
  "requestedChannels": "EMAIL,SMS",
  "priority": "HIGH",
  "payload": {
    "orderId": "ORD-2026-123456",
    "orderDate": "2026-11-05T10:30:00Z",
    "amount": 99.99,
    "currency": "USD",
    "items": [
      {
        "productId": "PROD-001",
        "name": "Widget Pro",
        "quantity": 2,
        "price": 49.99
      }
    ],
    "estimatedDelivery": "2026-11-08",
    "trackingUrl": "https://example.com/track/12345"
  }
}
```

**Response (202 Accepted - New Notification)**:
```json
{
  "notificationId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PENDING",
  "message": "Notification accepted for processing",
  "createdAt": "2026-11-05T12:34:56.123456"
}
```

**Response (202 Accepted - Duplicate)**:
```json
{
  "notificationId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PENDING",
  "message": "Duplicate notification. Using existing notification ID: 550e8400-e29b-41d4-a716-446655440000",
  "createdAt": "2026-11-05T12:34:56.123456"
}
```

---

## Error Responses

### 400 Bad Request — Missing Idempotency-Key
```json
{
  "error": "Idempotency-Key header is required",
  "status": 400
}
```

### 400 Bad Request — Validation Error
```json
{
  "error": "Validation failed",
  "details": {
    "eventType": "eventType is required",
    "priority": "Invalid priority. Must be one of: CRITICAL, HIGH, NORMAL, LOW",
    "requestedChannels": "requestedChannels is required"
  },
  "status": 400
}
```

### 400 Bad Request — No Recipient Provided
```json
{
  "error": "At least one of recipientEmail, recipientPhone, or recipientUserId must be provided",
  "status": 400
}
```

### 401 Unauthorized — Missing or Invalid API Key
```json
{
  "error": "API key is required",
  "status": 401
}
```

### 401 Unauthorized — Suspended Tenant
```json
{
  "error": "Tenant is suspended",
  "status": 401
}
```

### 500 Internal Server Error
```json
{
  "error": "Internal server error",
  "status": 500
}
```

---

## Idempotency

Idempotency prevents duplicate notifications when requests are retried due to network failures or client timeouts.

### Implementation

1. **Requirement**: Every notification submission must include an `Idempotency-Key` header
2. **Scope**: Idempotency is tenant-scoped (different tenants can use the same key)
3. **Uniqueness**: Enforced at the database level via `UNIQUE INDEX idx_tenant_idempotency`
4. **Behavior**: 
   - First request with key → Creates notification, returns 202 Accepted
   - Subsequent requests with same key → Returns existing notification, still returns 202 Accepted
5. **Format**: Any string (e.g., UUIDs, event IDs, timestamps, combination thereof)

### Example

Request 1:
```
POST /api/v1/notifications
X-API-KEY: pr_live_xxxxx
Idempotency-Key: order-12345-attempt-1
```

Response 1 (201 → HTTP 202 Accepted):
```json
{
  "notificationId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PENDING",
  "message": "Notification accepted for processing"
}
```

Network timeout. Client retries with same key:

Request 2:
```
POST /api/v1/notifications
X-API-KEY: pr_live_xxxxx
Idempotency-Key: order-12345-attempt-1
```

Response 2 (Same notification, still 202):
```json
{
  "notificationId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PENDING",
  "message": "Duplicate notification. Using existing notification ID: 550e8400-e29b-41d4-a716-446655440000"
}
```

---

## Validation Rules

The `NotificationRequest` DTO enforces:

| Field | Required | Rule |
|-------|----------|------|
| `eventType` | Yes | Non-blank string, max 100 chars |
| `recipientEmail` | Conditional | If provided, valid email format (handled by caller) |
| `recipientPhone` | Conditional | At least ONE of email/phone/userId required |
| `recipientUserId` | Conditional | ↑ At least ONE of email/phone/userId required |
| `requestedChannels` | Yes | Non-blank string, comma-separated (EMAIL,SMS,PUSH,IN_APP) |
| `priority` | Yes | CRITICAL \| HIGH \| NORMAL \| LOW (case-insensitive) |
| `payload` | No | JSON map (can be null or empty) |

**Custom Validation** (NotificationService):
- At least one recipient field must be provided
- Priority must be a valid enum value

---

## HTTP Status Codes

| Code | Meaning | Scenario |
|------|---------|----------|
| 202 | Accepted | Notification successfully submitted (new or duplicate) |
| 400 | Bad Request | Invalid request body, missing/invalid fields, validation error |
| 400 | Bad Request | Idempotency-Key header missing |
| 400 | Bad Request | No recipient specified |
| 401 | Unauthorized | Missing or invalid X-API-KEY |
| 401 | Unauthorized | Tenant is suspended |
| 500 | Server Error | Unexpected database or application error |

---

## Postman Testing Steps

### Step 1: Register a Tenant
```
POST http://localhost:8084/api/v1/admin/tenants
Headers:
  Content-Type: application/json
  X-ADMIN-SECRET: PulseRelay-Admin-2026-Change

Body:
{
  "organizationName": "TechCorp E-Commerce",
  "contactEmail": "admin@techcorp.com"
}
```

Expected Response (201 Created):
```json
{
  "tenantId": "550e8400-e29b-41d4-a716-446655440000",
  "organizationName": "TechCorp E-Commerce",
  "contactEmail": "admin@techcorp.com",
  "status": "ACTIVE",
  "createdAt": "2026-11-05T12:00:00",
  "apiKey": "pr_live_xxxxxxxxxxxxxxxxxxxxxx",
  "apiKeyWarning": "⚠️ Save your API key securely..."
}
```

**Save the `apiKey` for subsequent requests.**

---

### Step 2: Submit a Notification
```
POST http://localhost:8084/api/v1/notifications
Headers:
  Content-Type: application/json
  X-API-KEY: pr_live_xxxxxxxxxxxxxxxxxxxxxx
  Idempotency-Key: order-2026-11-05-001

Body:
{
  "eventType": "order_confirmation",
  "recipientEmail": "customer@example.com",
  "recipientPhone": "+1-555-0123",
  "recipientUserId": "cust_12345",
  "requestedChannels": "EMAIL,SMS",
  "priority": "HIGH",
  "payload": {
    "orderId": "ORD-2026-001",
    "amount": 150.00,
    "currency": "USD"
  }
}
```

Expected Response (202 Accepted):
```json
{
  "notificationId": "550e8400-e29b-41d4-a716-446655440001",
  "status": "PENDING",
  "message": "Notification accepted for processing",
  "createdAt": "2026-11-05T12:34:56.123456"
}
```

---

### Step 3: Test Idempotency (Duplicate Request)
```
POST http://localhost:8084/api/v1/notifications
Headers:
  Content-Type: application/json
  X-API-KEY: pr_live_xxxxxxxxxxxxxxxxxxxxxx
  Idempotency-Key: order-2026-11-05-001

Body:
{
  "eventType": "order_confirmation",
  "recipientEmail": "customer@example.com",
  "recipientPhone": "+1-555-0123",
  "recipientUserId": "cust_12345",
  "requestedChannels": "EMAIL,SMS",
  "priority": "HIGH",
  "payload": {
    "orderId": "ORD-2026-001",
    "amount": 150.00,
    "currency": "USD"
  }
}
```

Expected Response (202 Accepted — Same notification ID):
```json
{
  "notificationId": "550e8400-e29b-41d4-a716-446655440001",
  "status": "PENDING",
  "message": "Duplicate notification. Using existing notification ID: 550e8400-e29b-41d4-a716-446655440001",
  "createdAt": "2026-11-05T12:34:56.123456"
}
```

---

### Step 4: Test Missing Idempotency-Key
```
POST http://localhost:8084/api/v1/notifications
Headers:
  Content-Type: application/json
  X-API-KEY: pr_live_xxxxxxxxxxxxxxxxxxxxxx

Body:
{
  "eventType": "order_confirmation",
  "recipientEmail": "customer@example.com",
  "requestedChannels": "EMAIL",
  "priority": "HIGH"
}
```

Expected Response (400 Bad Request):
```json
{
  "error": "Idempotency-Key header is required",
  "status": 400
}
```

---

### Step 5: Test Invalid Request (No Recipient)
```
POST http://localhost:8084/api/v1/notifications
Headers:
  Content-Type: application/json
  X-API-KEY: pr_live_xxxxxxxxxxxxxxxxxxxxxx
  Idempotency-Key: test-no-recipient

Body:
{
  "eventType": "order_confirmation",
  "requestedChannels": "EMAIL",
  "priority": "HIGH"
}
```

Expected Response (400 Bad Request):
```json
{
  "error": "At least one of recipientEmail, recipientPhone, or recipientUserId must be provided",
  "status": 400
}
```

---

### Step 6: Test Invalid Priority
```
POST http://localhost:8084/api/v1/notifications
Headers:
  Content-Type: application/json
  X-API-KEY: pr_live_xxxxxxxxxxxxxxxxxxxxxx
  Idempotency-Key: test-invalid-priority

Body:
{
  "eventType": "order_confirmation",
  "recipientEmail": "customer@example.com",
  "requestedChannels": "EMAIL",
  "priority": "SUPER_URGENT"
}
```

Expected Response (400 Bad Request):
```json
{
  "error": "Validation failed",
  "details": {
    "priority": "Invalid priority. Must be one of: CRITICAL, HIGH, NORMAL, LOW"
  },
  "status": 400
}
```

---

### Step 7: Test Missing API Key
```
POST http://localhost:8084/api/v1/notifications
Headers:
  Content-Type: application/json
  Idempotency-Key: test-no-api-key

Body:
{
  "eventType": "order_confirmation",
  "recipientEmail": "customer@example.com",
  "requestedChannels": "EMAIL",
  "priority": "HIGH"
}
```

Expected Response (401 Unauthorized):
```json
{
  "error": "API key is required",
  "status": 401
}
```

---

## Architecture Decisions

### 1. Tenant Isolation
- Every notification belongs to exactly one tenant
- Tenant is extracted from the authenticated context (X-API-KEY)
- Tenant ID **never** accepted from request body
- Enforced via foreign key constraints

### 2. Idempotency
- Unique constraint at database level: `(tenant_id, idempotency_key)`
- Prevents duplicates even under concurrent requests
- Tenant-scoped: Different tenants can use same key
- Returns existing notification (not error) for duplicate requests

### 3. Validation
- Bean Validation (`@NotBlank`, `@NotNull`) for request-level validation
- Custom business logic validation in `NotificationService`
- At least one recipient field required (email/phone/userId)
- Priority enum values validated

### 4. Error Handling
- Global `GlobalExceptionHandler` for consistent error responses
- Specific handlers for validation, custom exceptions, and unexpected errors
- No sensitive data in error messages (e.g., no database details)

### 5. Status Tracking
- `PENDING`: Initial state after acceptance
- Future phases will transition through: PROCESSING → SENT or FAILED
- Used for retry policies and monitoring

### 6. JSON Payload
- Stored as `JSONB` in PostgreSQL for flexible query capability
- No schema validation (flexibility for different event types)
- Max depth/size enforced by application constraints

---

## Database Schema

```sql
CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    event_type VARCHAR(100) NOT NULL,
    recipient_email VARCHAR(255),
    recipient_phone VARCHAR(20),
    recipient_user_id VARCHAR(255),
    requested_channels VARCHAR(255) NOT NULL,
    priority VARCHAR(20) NOT NULL,
    payload JSONB,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    idempotency_key VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tenant_id ON notifications(tenant_id);
CREATE INDEX idx_status ON notifications(status);
CREATE INDEX idx_created_at ON notifications(created_at);
CREATE UNIQUE INDEX idx_tenant_idempotency ON notifications(tenant_id, idempotency_key);
```

---

## Security Considerations

1. **Tenant Isolation**: Tenant extracted from authenticated context, never from request
2. **Idempotency Key Uniqueness**: Enforced at database level, prevents duplicates
3. **Authentication**: All endpoints require valid X-API-KEY
4. **Suspended Tenants**: Cannot submit notifications (blocked by API key authentication)
5. **Error Messages**: Don't expose internal system details
6. **Payload Validation**: No script injection possible (JSON stored as data, not code)
7. **Logging**: Recipient data not logged (email/phone/userId omitted from logs)

---

## What's NOT Implemented (Future Phases)

- ❌ Kafka/message queue integration
- ❌ Provider delivery (SMS gateway, email service)
- ❌ Retry logic and exponential backoff
- ❌ Notification templates
- ❌ Delivery channel routing
- ❌ Webhook callbacks
- ❌ Analytics and metrics

---

## Git Commit Message

```
feat: implement notification API with idempotency and persistence

- Add Notification entity with tenant relationship and status tracking
- Implement POST /api/v1/notifications endpoint with Bean Validation
- Add tenant-scoped idempotency via Idempotency-Key header
- Create NotificationService with validation and persistence logic
- Add GlobalExceptionHandler for consistent error responses
- Enforce database-level uniqueness constraint on (tenant_id, idempotency_key)
- Add NotificationRequest/NotificationResponse DTOs
- Add NotificationRepository with idempotency queries
- Prevent duplicate notifications through first-occurrence wins strategy
- Return HTTP 202 Accepted for all successful submissions
```

---

## Verification Checklist

✅ Notification entity created with all required fields  
✅ REST endpoint POST /api/v1/notifications implemented  
✅ X-API-KEY authentication required  
✅ Idempotency-Key header required  
✅ Tenant extracted from SecurityContext (not from request)  
✅ Idempotency enforced at database level  
✅ HTTP 202 Accepted for successful submissions  
✅ Duplicate requests return existing notification  
✅ Bean Validation enforced  
✅ At least one recipient field validation  
✅ Priority enum validation  
✅ Global exception handler implemented  
✅ No sensitive data in logs or errors  
✅ Compiles successfully  
✅ No Kafka/provider delivery implementation  

---

## Files Summary

| Category | File | Purpose |
|----------|------|---------|
| Entity | `Notification.java` | Core domain model |
| DTO | `NotificationRequest.java` | Request validation |
| DTO | `NotificationResponse.java` | Response formatting |
| Service | `NotificationService.java` | Business logic |
| Controller | `NotificationController.java` | REST API endpoint |
| Repository | `NotificationRepository.java` | Database access |
| Exception | `NotificationException.java` | Custom exception |
| Handler | `GlobalExceptionHandler.java` | Error handling |

