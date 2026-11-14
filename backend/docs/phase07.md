# Phase 07: Configurable Notification Routing Policies

## Implementation Summary

Phase 07 implements tenant-specific notification routing policies, allowing each tenant to configure how different event types should be routed across delivery channels without modifying application code.

### Key Features
- Tenant-isolated routing policy management
- Per-event-type channel configuration (primary + fallback)
- Configurable priority levels (CRITICAL, HIGH, NORMAL, LOW)
- Policy enable/disable toggles
- Database-backed policy persistence
- Channel validation (EMAIL, SMS, PUSH, IN_APP)
- Duplicate policy prevention per tenant-event type combination

## Files Modified/Created

### Entity
- `src/main/java/com/pulserelay/entity/RoutingPolicy.java` - Core routing policy entity with tenant relationship

### Repository
- `src/main/java/com/pulserelay/repository/RoutingPolicyRepository.java` - Data access layer with tenant-isolated queries

### DTOs
- `src/main/java/com/pulserelay/dto/RoutingPolicyRequest.java` - Request DTO for creating/updating policies
- `src/main/java/com/pulserelay/dto/RoutingPolicyResponse.java` - Response DTO for policy data

### Service
- `src/main/java/com/pulserelay/service/RoutingPolicyService.java` - Business logic for policy management and resolution

### Controller
- `src/main/java/com/pulserelay/controller/RoutingPolicyController.java` - REST API endpoints for policy CRUD operations

### Database
- `src/main/resources/db/migration/V003__Create_routing_policies_table.sql` - Migration creating routing_policies table with constraints

## Routing Policy Explanation

A routing policy defines how notifications for a specific event type should be delivered within a tenant's system:

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "eventType": "ORDER_CONFIRMED",
  "primaryChannels": "EMAIL,PUSH",
  "fallbackChannels": "SMS",
  "priority": "HIGH",
  "enabled": true,
  "createdAt": "2026-11-14T13:14:00Z",
  "updatedAt": "2026-11-14T13:14:00Z"
}
```

### Policy Fields
- **eventType**: The notification event (e.g., ORDER_CONFIRMED, PAYMENT_FAILED, DELIVERY_UPDATE)
- **primaryChannels**: First choice delivery channels (comma-separated: EMAIL,SMS,PUSH,IN_APP)
- **fallbackChannels**: Alternative channels if primary channels fail
- **priority**: Delivery priority (CRITICAL, HIGH, NORMAL, LOW)
- **enabled**: Policy can be toggled on/off without deletion
- **tenant**: Ensures policies only apply to their owning tenant

### Policy Resolution
When processing a notification:
1. System identifies the tenant from API key
2. Looks up policy for the tenant + event type
3. If policy exists and enabled: retrieves primary and fallback channels
4. If policy missing or disabled: throws NotificationException (fail-fast approach)

This ensures no notifications are sent through arbitrary channels accidentally.

## Example Policies

### Scenario 1: Order Confirmation
```json
{
  "eventType": "ORDER_CONFIRMED",
  "primaryChannels": "EMAIL,PUSH",
  "fallbackChannels": "SMS",
  "priority": "HIGH",
  "enabled": true
}
```

### Scenario 2: Payment Failed (High Urgency)
```json
{
  "eventType": "PAYMENT_FAILED",
  "primaryChannels": "SMS,PUSH",
  "fallbackChannels": "EMAIL",
  "priority": "CRITICAL",
  "enabled": true
}
```

### Scenario 3: Delivery Update (Push Primary)
```json
{
  "eventType": "DELIVERY_UPDATE",
  "primaryChannels": "PUSH",
  "fallbackChannels": "SMS",
  "priority": "NORMAL",
  "enabled": true
}
```

### Scenario 4: Promotional Offer (Email Only)
```json
{
  "eventType": "PROMOTIONAL_OFFER",
  "primaryChannels": "EMAIL",
  "fallbackChannels": null,
  "priority": "LOW",
  "enabled": true
}
```

## API Endpoints

All endpoints require `X-API-KEY` header for tenant authentication and API key verification.

### Create Policy
```
POST /api/v1/routing-policies
Content-Type: application/json
X-API-KEY: {tenant_api_key}

{
  "eventType": "ORDER_CONFIRMED",
  "primaryChannels": "EMAIL,PUSH",
  "fallbackChannels": "SMS",
  "priority": "HIGH",
  "enabled": true
}

Response: 201 Created
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "eventType": "ORDER_CONFIRMED",
  "primaryChannels": "EMAIL,PUSH",
  "fallbackChannels": "SMS",
  "priority": "HIGH",
  "enabled": true,
  "createdAt": "2026-11-14T13:14:00Z",
  "updatedAt": "2026-11-14T13:14:00Z"
}
```

### List Policies
```
GET /api/v1/routing-policies
X-API-KEY: {tenant_api_key}

Response: 200 OK
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "eventType": "ORDER_CONFIRMED",
    "primaryChannels": "EMAIL,PUSH",
    "fallbackChannels": "SMS",
    "priority": "HIGH",
    "enabled": true,
    "createdAt": "2026-11-14T13:14:00Z",
    "updatedAt": "2026-11-14T13:14:00Z"
  }
]
```

### Get Policy
```
GET /api/v1/routing-policies/{policyId}
X-API-KEY: {tenant_api_key}

Response: 200 OK
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "eventType": "ORDER_CONFIRMED",
  ...
}
```

### Update Policy
```
PUT /api/v1/routing-policies/{policyId}
Content-Type: application/json
X-API-KEY: {tenant_api_key}

{
  "eventType": "ORDER_CONFIRMED",
  "primaryChannels": "EMAIL,PUSH,SMS",
  "fallbackChannels": null,
  "priority": "HIGH",
  "enabled": true
}

Response: 200 OK
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "eventType": "ORDER_CONFIRMED",
  "primaryChannels": "EMAIL,PUSH,SMS",
  "fallbackChannels": null,
  "priority": "HIGH",
  "enabled": true,
  "createdAt": "2026-11-14T13:14:00Z",
  "updatedAt": "2026-11-14T13:14:08Z"
}
```

### Enable Policy
```
PATCH /api/v1/routing-policies/{policyId}/enable
X-API-KEY: {tenant_api_key}

Response: 200 OK
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "eventType": "ORDER_CONFIRMED",
  "enabled": true,
  ...
}
```

### Disable Policy
```
PATCH /api/v1/routing-policies/{policyId}/disable
X-API-KEY: {tenant_api_key}

Response: 200 OK
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "eventType": "ORDER_CONFIRMED",
  "enabled": false,
  ...
}
```

### Delete Policy
```
DELETE /api/v1/routing-policies/{policyId}
X-API-KEY: {tenant_api_key}

Response: 204 No Content
```

## Postman Testing Steps

### Prerequisites
1. Ensure application is running on http://localhost:8084/api
2. Have valid tenant API key from tenant registration

### Test 1: Create Policy
```
Method: POST
URL: http://localhost:8084/api/v1/routing-policies
Headers:
  X-API-KEY: {your_tenant_api_key}
  Content-Type: application/json
Body:
{
  "eventType": "ORDER_CONFIRMED",
  "primaryChannels": "EMAIL,PUSH",
  "fallbackChannels": "SMS",
  "priority": "HIGH",
  "enabled": true
}
Expected: 201 Created with policy UUID in response
```

### Test 2: List Policies
```
Method: GET
URL: http://localhost:8084/api/v1/routing-policies
Headers:
  X-API-KEY: {your_tenant_api_key}
Expected: 200 OK with array of policies
```

### Test 3: Get Specific Policy
```
Method: GET
URL: http://localhost:8084/api/v1/routing-policies/{policyId}
Headers:
  X-API-KEY: {your_tenant_api_key}
Expected: 200 OK with single policy
```

### Test 4: Update Policy
```
Method: PUT
URL: http://localhost:8084/api/v1/routing-policies/{policyId}
Headers:
  X-API-KEY: {your_tenant_api_key}
  Content-Type: application/json
Body:
{
  "eventType": "ORDER_CONFIRMED",
  "primaryChannels": "EMAIL,PUSH,SMS",
  "fallbackChannels": null,
  "priority": "CRITICAL",
  "enabled": true
}
Expected: 200 OK with updated policy
```

### Test 5: Disable Policy
```
Method: PATCH
URL: http://localhost:8084/api/v1/routing-policies/{policyId}/disable
Headers:
  X-API-KEY: {your_tenant_api_key}
Expected: 200 OK with enabled=false
```

### Test 6: Enable Policy
```
Method: PATCH
URL: http://localhost:8084/api/v1/routing-policies/{policyId}/enable
Headers:
  X-API-KEY: {your_tenant_api_key}
Expected: 200 OK with enabled=true
```

### Test 7: Delete Policy
```
Method: DELETE
URL: http://localhost:8084/api/v1/routing-policies/{policyId}
Headers:
  X-API-KEY: {your_tenant_api_key}
Expected: 204 No Content
```

## Tenant Isolation Tests

### Test: Access Denial to Other Tenant's Policy
```
1. Register Tenant A and create policy P1
2. Register Tenant B 
3. Try to GET/UPDATE/DELETE policy P1 with Tenant B's API key
Expected: 403 Forbidden or "Access denied: Policy does not belong to this tenant"
```

### Test: List Policies Only Shows Tenant's Own Policies
```
1. Register Tenant A and create policies P1, P2
2. Register Tenant B and create policy P3
3. List policies for Tenant A
Expected: Only P1 and P2 (not P3)
4. List policies for Tenant B
Expected: Only P3 (not P1 or P2)
```

### Test: Duplicate Policy Prevention
```
1. Create policy for Tenant A with eventType="ORDER_CONFIRMED"
2. Try to create another policy for same Tenant A with same eventType
Expected: 409 Conflict or "Policy already exists for this tenant and event type"
```

## Validation & Error Handling

### Valid Channel Combinations (Accepted)
- "EMAIL"
- "SMS"
- "PUSH"
- "IN_APP"
- "EMAIL,SMS"
- "EMAIL,PUSH,SMS"
- "PUSH,IN_APP"

### Invalid Channel Combinations (Rejected)
- "TELEGRAM" → Invalid channel error
- "EMAIL,INVALID_CHANNEL" → Invalid channel error
- "" (empty string) → Required field error
- "EMAIL," → Trailing comma may cause validation

### Invalid Priorities (Rejected)
- "URGENT" → Invalid priority error
- "" (empty) → Required field error
- "high" (lowercase) → Case-sensitive, must be uppercase

### Expected Error Responses

**Missing Required Field**
```json
{
  "error": "eventType is required",
  "status": 400
}
```

**Invalid Channel**
```json
{
  "error": "Invalid channel: TELEGRAM. Valid channels are: EMAIL, SMS, PUSH, IN_APP",
  "status": 400
}
```

**Duplicate Policy**
```json
{
  "error": "Policy already exists for this tenant and event type: ORDER_CONFIRMED",
  "status": 409
}
```

**Policy Not Found**
```json
{
  "error": "Policy not found: 550e8400-e29b-41d4-a716-446655440000",
  "status": 404
}
```

**Access Denied**
```json
{
  "error": "Access denied: Policy does not belong to this tenant",
  "status": 403
}
```

## Database Schema

```sql
CREATE TABLE routing_policies (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    enabled BOOLEAN DEFAULT TRUE,
    primary_channels VARCHAR(255) NOT NULL,
    fallback_channels VARCHAR(255),
    priority VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    
    CONSTRAINT fk_routing_policy_tenant 
        FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT uq_tenant_event_type 
        UNIQUE (tenant_id, event_type)
);
```

### Indexes for Performance
- `idx_routing_policy_tenant_id` - Fast lookups by tenant
- `idx_routing_policy_tenant_event_type` - Fast policy resolution
- `idx_routing_policy_enabled` - Efficient filtering of disabled policies

## Implementation Notes

### Policy Resolution Logic
The `RoutingPolicyService.resolvePolicy()` method:
1. Looks up policy by tenant ID and event type
2. Throws `NotificationException` if not found (fail-fast)
3. Checks if policy is enabled, throws if disabled
4. Returns policy for use by notification processor

### Tenant Isolation Pattern
Every query filters by `tenant_id`:
- `findByTenantIdAndEventType()` - Always scoped to tenant
- `findByTenantId()` - Only returns tenant's policies
- All mutations verify tenant ownership before proceeding

### Channel Storage Format
Channels stored as comma-separated strings for simplicity:
- "EMAIL,SMS,PUSH" - Multiple channels
- "PUSH" - Single channel
- No spaces after commas for consistency

### Validation Strategy
- Channels validated against whitelist: {EMAIL, SMS, PUSH, IN_APP}
- Priorities validated against enum: {CRITICAL, HIGH, NORMAL, LOW}
- Duplicate prevention via database UNIQUE constraint
- Invalid configurations caught at service layer before DB

## Future Enhancements (Phase 08+)

The following are NOT implemented in Phase 07 per requirements:

1. **Fallback Execution** - Automatic channel fallback if primary fails
2. **Template System** - Message templates with variable substitution
3. **External Provider Integration** - Connecting to email/SMS/push services
4. **Frontend UI** - Web interface for policy management
5. **Policy Conditions** - Conditional routing based on recipient attributes
6. **Retry Logic for Fallback** - Coordinated retry with fallback channels
7. **Policy Versioning** - History tracking of policy changes
8. **Batch Operations** - Create/update multiple policies in single request

## Git Commit Message

```
feat(phase07): configurable notification routing policies

- Add RoutingPolicy entity with tenant relationship and constraints
- Implement RoutingPolicyRepository with tenant-isolated queries
- Create RoutingPolicyService with validation and policy resolution
- Add RoutingPolicyController with full CRUD REST endpoints
- Create database migration V003 with routing_policies table
- Add comprehensive policy management endpoints:
  * POST /routing-policies (create)
  * GET /routing-policies (list)
  * GET /routing-policies/{id} (get)
  * PUT /routing-policies/{id} (update)
  * PATCH /routing-policies/{id}/enable (toggle)
  * PATCH /routing-policies/{id}/disable (toggle)
  * DELETE /routing-policies/{id} (delete)
- Enforce tenant isolation on all operations
- Validate channels (EMAIL,SMS,PUSH,IN_APP) and priorities
- Prevent duplicate policies per tenant-event type via unique constraint
- Implement fail-fast policy resolution (no silent fallbacks)
- Add error handling with clear error messages
```
