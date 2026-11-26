# Backend API Audit - Complete Reference

## Overview
Backend runs on `http://localhost:8084/api`
All APIs use `X-API-KEY` header for authentication (except admin endpoints which use `X-ADMIN-SECRET`)

---

## Authentication & Security

### Auth Mechanism
- **X-API-KEY Header**: Tenant-specific API key validated against PostgreSQL
- **X-ADMIN-SECRET Header**: Admin secret for privileged operations
- **Context Path**: `/api` (all URLs relative to this)
- **Session**: Stateless (SessionCreationPolicy.STATELESS)

### Public Endpoints (No Auth Required)
- `GET /actuator/health` - Health check
- `GET /actuator/info` - Application info
- `POST /v1/admin/tenants` - Register tenant (requires X-ADMIN-SECRET header)

---

## Tenant Management APIs

### 1. Get Authenticated Tenant Info
```
GET /v1/tenant/me
```
**Authentication**: X-API-KEY header
**Response (200 OK)**:
```json
{
  "tenantId": "uuid",
  "organizationName": "string",
  "contactEmail": "string",
  "status": "ACTIVE|SUSPENDED"
}
```
**Error Responses**:
- 401: Missing or invalid X-API-KEY
- 500: Internal server error

---

### 2. Register New Tenant (Admin Only)
```
POST /v1/admin/tenants
```
**Authentication**: X-ADMIN-SECRET header (required)
**Request Body**:
```json
{
  "organizationName": "string (required, 2-255 chars)",
  "contactEmail": "string (required, valid email)"
}
```
**Response (201 Created)**:
```json
{
  "tenantId": "uuid",
  "organizationName": "string",
  "contactEmail": "string",
  "status": "ACTIVE",
  "createdAt": "ISO-8601 datetime",
  "apiKey": "pr_live_xxxxx",
  "apiKeyWarning": "string"
}
```
**Validation**:
- organizationName: required, 2-255 characters
- contactEmail: required, valid email format
**Error Responses**:
- 400: Validation error
- 403: Invalid or missing X-ADMIN-SECRET

---

## Notification APIs

### 1. Submit Notification
```
POST /v1/notifications
```
**Authentication**: X-API-KEY header (required)
**Headers Required**:
- `X-API-KEY`: Tenant API key
- `Idempotency-Key`: Unique key for idempotency (required)

**Request Body**:
```json
{
  "eventType": "string (required, 1-100 chars)",
  "recipientEmail": "string (optional)",
  "recipientPhone": "string (optional)",
  "recipientUserId": "string (optional)",
  "requestedChannels": "string (required, comma-separated: EMAIL,SMS,PUSH,IN_APP)",
  "priority": "CRITICAL|HIGH|NORMAL|LOW (required)",
  "payload": {
    "key": "value"  // Optional JSON object for template variables
  }
}
```
**Validation**:
- At least one recipient required (email, phone, or userId)
- eventType: required, 1-100 chars max
- requestedChannels: required, valid channels
- priority: required, one of CRITICAL|HIGH|NORMAL|LOW
- Idempotency-Key header: required

**Response (202 Accepted)**:
```json
{
  "notificationId": "uuid",
  "status": "PENDING",
  "message": "Notification accepted for processing",
  "createdAt": "ISO-8601 datetime"
}
```
**Error Responses**:
- 400: Missing Idempotency-Key or validation error
- 401: Missing/invalid X-API-KEY or suspended tenant
- 500: Internal server error

**Idempotency Behavior**:
- Same `tenantId + Idempotency-Key` within same call returns existing notification
- Response still 202 Accepted with same notificationId

---

### 2. Test Delivery (Synchronous)
```
POST /v1/notifications/test-delivery
```
**Authentication**: X-API-KEY header (required)

**Request Body**:
```json
{
  "channel": "EMAIL|SMS|PUSH|IN_APP (required)",
  "recipient": "string (required, email/phone/token)",
  "subject": "string (optional, for EMAIL)",
  "body": "string (required)"
}
```
**Validation**:
- channel: required, valid channel
- recipient: required, non-blank
- body: required, non-blank
- subject: optional

**Response (200 OK)**:
```json
{
  "id": "uuid (delivery attempt ID)",
  "status": "ACCEPTED|DELIVERED|FAILED|PERMANENT_FAILURE|RETRY_SCHEDULED",
  "channel": "EMAIL|SMS|PUSH|IN_APP",
  "provider": "string (e.g., 'MockEmail', 'Smtp')",
  "recipient": "string",
  "providerMessageId": "string|null",
  "errorCode": "string|null",
  "errorMessage": "string|null",
  "isRetryable": "boolean",
  "attemptedAt": "ISO-8601 datetime",
  "nextAttemptAt": "ISO-8601 datetime|null"
}
```
**Error Responses**:
- 400: Validation error or unsupported channel
- 401: Missing/invalid X-API-KEY
- 500: Internal server error

---

## Notification Template APIs

### 1. List Templates
```
GET /v1/notification-templates
```
**Authentication**: X-API-KEY header
**Response (200 OK)**:
```json
[
  {
    "id": "uuid",
    "name": "string",
    "eventType": "string",
    "channel": "EMAIL|SMS|PUSH|IN_APP",
    "subject": "string|null",
    "body": "string",
    "enabled": "boolean",
    "createdAt": "ISO-8601 datetime",
    "updatedAt": "ISO-8601 datetime"
  }
]
```

---

### 2. Get Template
```
GET /v1/notification-templates/{templateId}
```
**Authentication**: X-API-KEY header
**Path Parameters**:
- `templateId`: UUID of template
**Response (200 OK)**: Single template object (same structure as list)

---

### 3. Create Template
```
POST /v1/notification-templates
```
**Authentication**: X-API-KEY header
**Request Body**:
```json
{
  "name": "string (required)",
  "eventType": "string (required)",
  "channel": "EMAIL|SMS|PUSH|IN_APP (required)",
  "subject": "string (optional)",
  "body": "string (required)",
  "enabled": "boolean (required)"
}
```
**Validation**:
- name: required, non-blank
- eventType: required, non-blank
- channel: required, valid channel
- body: required, non-blank
- enabled: required, boolean

**Response (201 Created)**: Created template object

---

### 4. Update Template
```
PUT /v1/notification-templates/{templateId}
```
**Authentication**: X-API-KEY header
**Request Body**: Same as Create (all fields required)
**Response (200 OK)**: Updated template object

---

### 5. Enable Template
```
PATCH /v1/notification-templates/{templateId}/enable
```
**Authentication**: X-API-KEY header
**Response (200 OK)**: Updated template object with `enabled: true`

---

### 6. Disable Template
```
PATCH /v1/notification-templates/{templateId}/disable
```
**Authentication**: X-API-KEY header
**Response (200 OK)**: Updated template object with `enabled: false`

---

### 7. Delete Template
```
DELETE /v1/notification-templates/{templateId}
```
**Authentication**: X-API-KEY header
**Response (204 No Content)**: Empty response

---

### 8. Render Template
```
POST /v1/notification-templates/render
```
**Authentication**: X-API-KEY header
**Request Body**:
```json
{
  "templateId": "uuid (required)",
  "variables": {
    "key": "value"  // Template variables
  }
}
```
**Response (200 OK)**:
```json
{
  "templateId": "uuid",
  "renderedSubject": "string",
  "renderedBody": "string"
}
```

---

## Routing Policy APIs

### 1. List Policies
```
GET /v1/routing-policies
```
**Authentication**: X-API-KEY header
**Response (200 OK)**:
```json
[
  {
    "id": "uuid",
    "eventType": "string",
    "primaryChannels": "string (comma-separated)",
    "fallbackChannels": "string|null (comma-separated)",
    "priority": "CRITICAL|HIGH|NORMAL|LOW",
    "enabled": "boolean",
    "createdAt": "ISO-8601 datetime",
    "updatedAt": "ISO-8601 datetime"
  }
]
```

---

### 2. Get Policy
```
GET /v1/routing-policies/{policyId}
```
**Authentication**: X-API-KEY header
**Path Parameters**:
- `policyId`: UUID of policy
**Response (200 OK)**: Single policy object

---

### 3. Create Policy
```
POST /v1/routing-policies
```
**Authentication**: X-API-KEY header
**Request Body**:
```json
{
  "eventType": "string (required)",
  "primaryChannels": "string (required, comma-separated)",
  "fallbackChannels": "string (optional, comma-separated)",
  "priority": "CRITICAL|HIGH|NORMAL|LOW (required)",
  "enabled": "boolean (optional, defaults to true)"
}
```
**Validation**:
- eventType: required, non-blank
- primaryChannels: required, comma-separated list of valid channels
- priority: required, one of CRITICAL|HIGH|NORMAL|LOW
- enabled: optional, defaults to true if not provided

**Response (201 Created)**: Created policy object

---

### 4. Update Policy
```
PUT /v1/routing-policies/{policyId}
```
**Authentication**: X-API-KEY header
**Request Body**: Same as Create (all fields required)
**Response (200 OK)**: Updated policy object

---

### 5. Enable Policy
```
PATCH /v1/routing-policies/{policyId}/enable
```
**Authentication**: X-API-KEY header
**Response (200 OK)**: Updated policy object with `enabled: true`

---

### 6. Disable Policy
```
PATCH /v1/routing-policies/{policyId}/disable
```
**Authentication**: X-API-KEY header
**Response (200 OK)**: Updated policy object with `enabled: false`

---

### 7. Delete Policy
```
DELETE /v1/routing-policies/{policyId}
```
**Authentication**: X-API-KEY header
**Response (204 No Content)**: Empty response

---

## Status Enums

### Notification Status
- `PENDING` - Notification queued for processing

### Delivery Status
- `ACCEPTED` - Provider accepted (not delivery confirmation)
- `DELIVERED` - Message confirmed delivered
- `FAILED` - Delivery failed, potentially retryable
- `PERMANENT_FAILURE` - Non-retryable failure
- `RETRY_SCHEDULED` - Scheduled for automatic retry

### Tenant Status
- `ACTIVE` - Tenant can send notifications
- `SUSPENDED` - Tenant blocked from sending

### Channel Types
- `EMAIL`
- `SMS`
- `PUSH`
- `IN_APP`

### Priority Levels
- `CRITICAL` - Highest priority
- `HIGH` - High priority
- `NORMAL` - Normal priority
- `LOW` - Low priority

---

## Error Response Format
```json
{
  "error": "string (error message)",
  "status": "integer (HTTP status code)"
}
```

---

## HTTP Status Codes Summary

| Code | Meaning | Endpoints |
|------|---------|-----------|
| 200 | OK | GET, PATCH (GET template data) |
| 201 | Created | POST (create operations) |
| 202 | Accepted | POST /notifications |
| 204 | No Content | DELETE operations |
| 400 | Bad Request | Invalid input or missing required fields |
| 401 | Unauthorized | Missing/invalid API key or suspended tenant |
| 403 | Forbidden | Invalid admin secret |
| 500 | Internal Server Error | Unexpected server error |

---

## Base URL
```
http://localhost:8084/api
```

All endpoints are relative to this base URL.

---

## Example Flow

### 1. Register tenant (admin)
```
POST /api/v1/admin/tenants
X-ADMIN-SECRET: admin-secret-key-2026
Content-Type: application/json

{
  "organizationName": "ACME Corp",
  "contactEmail": "admin@acme.com"
}
```

### 2. Get tenant info
```
GET /api/v1/tenant/me
X-API-KEY: pr_live_xxxxx
```

### 3. Create template
```
POST /api/v1/notification-templates
X-API-KEY: pr_live_xxxxx
Content-Type: application/json

{
  "name": "Order Confirmation",
  "eventType": "order_confirmed",
  "channel": "EMAIL",
  "subject": "Your order {{orderId}}",
  "body": "Thank you for your order!",
  "enabled": true
}
```

### 4. Create routing policy
```
POST /api/v1/routing-policies
X-API-KEY: pr_live_xxxxx
Content-Type: application/json

{
  "eventType": "order_confirmed",
  "primaryChannels": "EMAIL",
  "fallbackChannels": null,
  "priority": "HIGH",
  "enabled": true
}
```

### 5. Submit notification
```
POST /api/v1/notifications
X-API-KEY: pr_live_xxxxx
Idempotency-Key: order-12345-20261126
Content-Type: application/json

{
  "eventType": "order_confirmed",
  "recipientEmail": "customer@example.com",
  "requestedChannels": "EMAIL",
  "priority": "HIGH",
  "payload": {
    "orderId": "12345",
    "amount": 99.99
  }
}
```

### 6. Test delivery
```
POST /api/v1/notifications/test-delivery
X-API-KEY: pr_live_xxxxx
Content-Type: application/json

{
  "channel": "EMAIL",
  "recipient": "test@example.com",
  "subject": "Test Subject",
  "body": "Test message"
}
```

