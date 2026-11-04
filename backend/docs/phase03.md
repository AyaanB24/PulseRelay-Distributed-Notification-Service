# Phase 03 — Multi-Tenant Registration and API Key Authentication

## Implementation Summary

Phase 03 implements multi-tenant support and API key authentication for PulseRelay. Different organizations can register as separate tenants with isolated data, each receiving a cryptographically secure API key for authentication. The system enforces tenant isolation and provides secure API key management with proper hashing and revocation support.

### What Was Implemented

- ✅ **Tenant Entity** with UUID ID, organization name, contact email, status (ACTIVE/SUSPENDED), timestamps
- ✅ **API Key Entity** with secure SHA-256 hashing, human-readable names, status tracking
- ✅ **Cryptographic API Key Generation** using SecureRandom with recognizable `pr_live_` prefix
- ✅ **API Key Authentication Filter** that validates requests and sets tenant context
- ✅ **Admin Tenant Registration Endpoint** protected by admin secret
- ✅ **Multi-tenant Security** with tenant suspension and API key revocation
- ✅ **Spring Security Integration** with stateless authentication and role-based access control

## Files Created

### Entities (src/main/java/com/pulserelay/entity/)

1. **`Tenant.java`**
   - UUID primary key
   - organizationName (unique, indexed)
   - contactEmail (unique, indexed)
   - status (ACTIVE or SUSPENDED)
   - Timestamps with JPA lifecycle hooks
   - Database indexes for performance

2. **`ApiKey.java`**
   - UUID primary key
   - Relationship to Tenant (ManyToOne, LAZY fetch)
   - keyHash (SHA-256 hash, unique, indexed)
   - name (human-readable identifier)
   - status (ACTIVE or REVOKED)
   - lastUsedAt (for audit tracking)
   - Timestamps

### DTOs (src/main/java/com/pulserelay/dto/)

1. **`TenantRegistrationRequest.java`**
   - organizationName (validated: required, 2-255 chars)
   - contactEmail (validated: required, valid email)
   - Bean validation annotations for input validation

2. **`TenantRegistrationResponse.java`**
   - tenantId (UUID)
   - organizationName
   - contactEmail
   - status
   - createdAt
   - apiKey (original key, shown only once)
   - apiKeyWarning (security reminder)

### Repositories (src/main/java/com/pulserelay/repository/)

1. **`TenantRepository.java`**
   - findByOrganizationName()
   - findByContactEmail()
   - Uniqueness checks for duplicate prevention

2. **`ApiKeyRepository.java`**
   - findByKeyHash() (used during authentication)
   - findActiveKeyWithTenant() (optimized query with JOIN FETCH)
   - Status and revocation tracking

### Services (src/main/java/com/pulserelay/service/)

1. **`TenantService.java`**
   - registerTenant() - Creates new tenant with validation
   - getTenantById() - Retrieves tenant by ID
   - suspendTenant() / activateTenant() - Status management

2. **`ApiKeyService.java`**
   - generateRawApiKey() - Cryptographically secure generation (256-bit random)
   - hashApiKey() - SHA-256 hashing (only hash stored)
   - createApiKey() - Creates and stores key for tenant
   - authenticateApiKey() - Validates key during request authentication
   - revokeApiKey() - Marks key as revoked

### Security (src/main/java/com/pulserelay/config/)

1. **`ApiKeyAuthenticationFilter.java`**
   - OncePerRequestFilter implementation
   - Reads X-API-KEY header
   - Authenticates key and sets tenant context
   - Skips authentication for public endpoints
   - Returns 401 for missing/invalid keys

2. **`TenantPrincipal.java`**
   - Spring Security UserDetails implementation
   - Wraps tenant information
   - Provides ROLE_TENANT authority
   - Accessible via SecurityContextHolder

3. **`SecurityConfig.java`**
   - Configures Spring Security
   - Stateless session management
   - Public endpoint whitelist
   - Filter chain configuration

### Controllers (src/main/java/com/pulserelay/controller/)

1. **`TenantAdminController.java`**
   - POST /api/v1/admin/tenants - Register new tenant
   - Requires X-ADMIN-SECRET header
   - Constant-time secret comparison (timing attack prevention)
   - Returns 201 with API key (shown only once)

### Exceptions (src/main/java/com/pulserelay/exception/)

1. **`TenantException.java`** - Tenant operation failures
2. **`ApiKeyException.java`** - API key operation failures

### Configuration Updates

**application.yml**
- Added pulserelay.admin.secret configuration
- Environment variable: PULSERELAY_ADMIN_SECRET
- Spring Security DEBUG logging enabled

## Architecture

### Multi-Tenant Design

```
┌─────────────────────────────────────────────────────┐
│           PulseRelay Platform (Shared)              │
├─────────────────────────────────────────────────────┤
│                                                     │
│  ┌──────────────────┐      ┌──────────────────┐   │
│  │   Tenant 1       │      │   Tenant 2       │   │
│  │  (Flipkart)      │      │   (Amazon)       │   │
│  ├──────────────────┤      ├──────────────────┤   │
│  │ ID: UUID-1       │      │ ID: UUID-2       │   │
│  │ Keys:            │      │ Keys:            │   │
│  │  - pr_live_xxx1  │      │  - pr_live_yyy1  │   │
│  │  - pr_live_xxx2  │      │  - pr_live_yyy2  │   │
│  │ Status: ACTIVE   │      │ Status: ACTIVE   │   │
│  └──────────────────┘      └──────────────────┘   │
│                                                     │
│  ┌──────────────────┐                              │
│  │   Tenant 3       │                              │
│  │  (Suspended)     │                              │
│  ├──────────────────┤                              │
│  │ Status: SUSPENDED│                              │
│  │ Keys: Rejected   │                              │
│  └──────────────────┘                              │
│                                                     │
└─────────────────────────────────────────────────────┘
```

### API Key Lifecycle

```
1. GENERATION
   └─ SecureRandom.nextBytes(32)  → Random 256-bit value
   └─ Base64 encode               → URL-safe string
   └─ Prefix with "pr_live_"      → Final key: pr_live_xxxxx...

2. STORAGE
   └─ Hash: SHA-256(apiKey)       → 64-char hex string
   └─ Store hash in database      → Original key never persisted
   └─ Return original key once    → Client must save

3. AUTHENTICATION
   ├─ Client sends: X-API-KEY: pr_live_xxxxx...
   └─ Server: Hash(received_key) == stored_hash?
      ├─ YES: Load tenant, check status
      │       ├─ ACTIVE: Allow request
      │       └─ SUSPENDED: Reject (403)
      └─ NO: Reject (401)

4. REVOCATION
   └─ Set status: REVOKED
   └─ Future auth attempts: Not found (401)
```

### Request Authentication Flow

```
Client Request
    │
    ├─ Has X-API-KEY header?
    │  ├─ NO  → 401 Unauthorized
    │  └─ YES → Continue
    │
    ├─ Hash the key
    │
    ├─ Find active key in database
    │  ├─ NOT FOUND → 401 Unauthorized
    │  └─ FOUND     → Continue
    │
    ├─ Check tenant status
    │  ├─ SUSPENDED → 403 Forbidden
    │  └─ ACTIVE    → Continue
    │
    ├─ Create TenantPrincipal
    │
    ├─ Set SecurityContext
    │
    └─ Allow request → Downstream handler
```

## API Endpoints

### Tenant Registration (Public)

**Endpoint:** `POST /api/v1/admin/tenants`

**Headers:**
```
X-ADMIN-SECRET: <admin-secret-from-environment>
Content-Type: application/json
```

**Request Body:**
```json
{
  "organizationName": "Flipkart",
  "contactEmail": "admin@flipkart.com"
}
```

**Response (201 Created):**
```json
{
  "tenantId": "550e8400-e29b-41d4-a716-446655440000",
  "organizationName": "Flipkart",
  "contactEmail": "admin@flipkart.com",
  "status": "ACTIVE",
  "createdAt": "2026-11-04T12:34:56",
  "apiKey": "pr_live_4J3p9kXvW2nM7qRs5tUvWxYzA",
  "apiKeyWarning": "⚠️ Save your API key securely. It will not be shown again. If lost, revoke this key and generate a new one."
}
```

**Error Response (401 Missing Secret):**
```json
{
  "error": "Invalid or missing admin secret",
  "status": 401
}
```

**Error Response (400 Validation Failed):**
```json
{
  "organizationName": "must not be blank",
  "contactEmail": "must be a valid email address"
}
```

## Environment Configuration

### Windows Environment Variables

Set these variables for the application to function:

```bash
# Database (Phase 02)
set DB_NAME=pulserelay_dev
set DB_USER=pulserelay
set DB_PASSWORD=postgres

# Admin Secret (Phase 03) - REQUIRED
set PULSERELAY_ADMIN_SECRET=your-secure-admin-secret-here
```

**Important:** Change the admin secret to a strong, random value in production.

### Via application.yml

Alternatively, set in `application.yml`:

```yaml
pulserelay:
  admin:
    secret: ${PULSERELAY_ADMIN_SECRET}
```

## Security Considerations

### API Key Security

1. **Secure Generation**
   - Uses `SecureRandom` for cryptographic randomness
   - 256-bit (32 bytes) entropy per key
   - URL-safe base64 encoding

2. **Secure Storage**
   - Only SHA-256 hash stored in database
   - Original key never logged or persisted
   - Hash indexed for O(1) lookup

3. **Secure Transmission**
   - API key travels in X-API-KEY header
   - Should use HTTPS in production
   - No key in URL or request body

4. **Revocation**
   - Keys can be marked as REVOKED
   - Revoked keys rejected immediately
   - No way to "unrevoke" (create new key instead)

### Admin Secret Security

1. **Constant-Time Comparison**
   - Prevents timing attacks on secret validation
   - Takes same time regardless of length of mismatch

2. **Environment Variable**
   - Never committed to source code
   - Set at deployment time
   - Different per environment

3. **Single Use Per Request**
   - Validated on every tenant registration request
   - No session or token reuse

### Tenant Isolation

1. **Database Constraints**
   - organizationName: UNIQUE
   - contactEmail: UNIQUE
   - Prevents tenant name collisions

2. **Multi-Tenant Queries**
   - All queries filtered by tenant context from API key
   - Tenant ID extracted from SecurityContext
   - No client can specify arbitrary tenant ID

3. **Suspension Mechanism**
   - Suspended tenants rejected even with valid key
   - Prevents data access during investigations
   - Immediate effect (no grace period)

## Postman Testing

### Test 1: Register a Tenant

```
POST http://localhost:8084/api/v1/admin/tenants
Headers:
  X-ADMIN-SECRET: your-admin-secret-value
  Content-Type: application/json

Body (raw JSON):
{
  "organizationName": "Flipkart",
  "contactEmail": "admin@flipkart.com"
}
```

**Expected Response (201 Created):**
- Returns tenantId and apiKey
- API key starts with `pr_live_`
- Status: ACTIVE
- Warning about saving the key

### Test 2: Register with Invalid Admin Secret

```
POST http://localhost:8084/api/v1/admin/tenants
Headers:
  X-ADMIN-SECRET: wrong-secret
  Content-Type: application/json

Body:
{
  "organizationName": "Amazon",
  "contactEmail": "admin@amazon.com"
}
```

**Expected Response (401 Unauthorized):**
```json
{
  "error": "Invalid or revoked API key",
  "status": 401
}
```

### Test 3: Register with Duplicate Organization Name

```
POST http://localhost:8084/api/v1/admin/tenants
Headers:
  X-ADMIN-SECRET: your-admin-secret-value
  Content-Type: application/json

Body:
{
  "organizationName": "Flipkart",
  "contactEmail": "admin2@flipkart.com"
}
```

**Expected Response (400 Bad Request):**
```json
{
  "error": "Organization name 'Flipkart' already exists"
}
```

### Test 4: Health Check (No Authentication Required)

```
GET http://localhost:8084/api/actuator/health
```

**Expected Response (200 OK):**
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP"
    }
  }
}
```

### Test 5: Access Protected Endpoint Without API Key

```
GET http://localhost:8084/api/v1/notifications
(No X-API-KEY header)
```

**Expected Response (401 Unauthorized):**
```json
{
  "error": "API key is required",
  "status": 401
}
```

## Database Schema

### Tenants Table

```sql
CREATE TABLE tenants (
    id UUID PRIMARY KEY,
    organization_name VARCHAR(255) NOT NULL UNIQUE,
    contact_email VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    INDEX idx_organization_name (organization_name),
    INDEX idx_status (status)
);
```

### API Keys Table

```sql
CREATE TABLE api_keys (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    key_hash VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    last_used_at TIMESTAMP,
    FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    INDEX idx_tenant_id (tenant_id),
    INDEX idx_key_hash (key_hash),
    INDEX idx_status (status)
);
```

## What's NOT Included

- ✗ Notification creation or sending
- ✗ Kafka or message queues
- ✗ Redis or caching
- ✗ Notification history or delivery tracking
- ✗ Frontend or UI
- ✗ API key list/management endpoints (for future phases)
- ✗ OAuth2 or JWT authentication

## Verification Checklist

- [x] Tenant entity created with all required fields
- [x] API key entity with SHA-256 hashing
- [x] Cryptographically secure key generation
- [x] API key authentication filter implemented
- [x] TenantPrincipal created for security context
- [x] Admin tenant registration endpoint protected
- [x] Admin secret from environment variable
- [x] Multi-tenant isolation enforced
- [x] Tenant suspension support
- [x] API key revocation support
- [x] Spring Security configuration
- [x] Public endpoints whitelisted
- [x] DTOs with bean validation
- [x] Exception handling
- [x] Logging configured
- [x] Database indexes for performance
- [x] No API key stored as plain text
- [x] No API key logged
- [x] Constant-time secret comparison

## Git Commit Message

```
feat: implement multi-tenant registration and API key authentication

- Create Tenant entity with UUID ID, organization name, contact email, status
- Create ApiKey entity with SHA-256 hash storage, revocation support
- Implement cryptographically secure API key generation (256-bit random)
- Add ApiKeyAuthenticationFilter for X-API-KEY header validation
- Add TenantPrincipal for SecurityContext tenant representation
- Implement admin-only tenant registration endpoint
- Add admin secret validation with constant-time comparison
- Create TenantService for tenant lifecycle management
- Create ApiKeyService for secure key generation and authentication
- Configure Spring Security for stateless API key authentication
- Add database indexes for performance optimization
- Include environment-based admin secret configuration

Enables multi-tenant support with isolated data per organization.
API keys are cryptographically secure with SHA-256 hashing.
No plain-text keys are stored or logged.
```

## Next Phase

Phase 04 will implement:
- Notification entity and model
- Notification creation and queuing
- Status tracking for notifications
- Delivery attempt logging

Current implementation provides the foundation for multi-tenant notification delivery.
