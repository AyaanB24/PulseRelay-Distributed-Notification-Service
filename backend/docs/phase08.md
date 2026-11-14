# Phase 08: Notification Template Management and Rendering

## Implementation Summary

Phase 08 implements tenant-specific notification templates that allow customers to define reusable content with variables for different notification channels. Templates support safe variable substitution using a simple placeholder system (e.g., `{{orderId}}`), enabling dynamic content without arbitrary code execution.

### Key Features
- Tenant-isolated template management
- Multi-channel template support (EMAIL, SMS, PUSH, IN_APP)
- Safe variable substitution with {{variable}} syntax
- Template enable/disable toggles
- Database-backed template persistence
- Duplicate prevention per tenant-event-channel combination
- Template rendering service with missing variable detection
- No external template engine required (safe regex-based substitution)

## Files Modified/Created

### Entity
- `src/main/java/com/pulserelay/entity/NotificationTemplate.java` - Core template entity with tenant relationship

### Repository
- `src/main/java/com/pulserelay/repository/NotificationTemplateRepository.java` - Data access layer with tenant-isolated queries

### DTOs
- `src/main/java/com/pulserelay/dto/NotificationTemplateRequest.java` - Request DTO for creating/updating templates
- `src/main/java/com/pulserelay/dto/NotificationTemplateResponse.java` - Response DTO for template data
- `src/main/java/com/pulserelay/dto/TemplateRenderRequest.java` - Request DTO for template rendering
- `src/main/java/com/pulserelay/dto/TemplateRenderResponse.java` - Response DTO for rendered output

### Service
- `src/main/java/com/pulserelay/service/NotificationTemplateService.java` - Business logic for template management and rendering

### Controller
- `src/main/java/com/pulserelay/controller/NotificationTemplateController.java` - REST API endpoints for templates

### Database
- `src/main/resources/db/migration/V004__Create_notification_templates_table.sql` - Migration creating notification_templates table

## Template Rendering Explanation

Template rendering is the process of replacing variable placeholders in a template with actual values from a notification payload. This enables dynamic, personalized content without storing multiple versions of the same template.

### Rendering Process
1. Retrieve template by ID (with tenant isolation)
2. Verify template is enabled (throw error if disabled)
3. Extract payload variables from request
4. Use regex to find all placeholders: `{{variableName}}`
5. For each placeholder:
   - Look up corresponding value in payload
   - Throw error if variable is missing (fail-fast)
   - Convert value to string safely
   - Replace placeholder with value
6. Return rendered subject and body

### Safety Features
- **No Expression Evaluation**: Placeholders are simple variable names, not expressions
- **No Code Execution**: Uses regex substitution, not template engines
- **Strict Variable Names**: Only alphanumeric + underscore (e.g., `{{order_id}}` is valid, `{{obj.method()}}` is not)
- **Missing Variable Detection**: Throws error immediately if required variable not in payload
- **Special Character Escaping**: Safely escapes special regex characters in values

### Example Rendering Flow
```
Template:
  Subject: "Order {{orderId}} Confirmation"
  Body: "Hello {{customerName}}, your order worth ${{amount}} is confirmed."

Payload:
{
  "orderId": "ORD-12345",
  "customerName": "John Doe",
  "amount": "99.99"
}

Rendered Output:
  Subject: "Order ORD-12345 Confirmation"
  Body: "Hello John Doe, your order worth $99.99 is confirmed."
```

## Example Templates

### Template 1: Order Confirmation Email
```json
{
  "name": "Order Confirmation",
  "eventType": "ORDER_CONFIRMED",
  "channel": "EMAIL",
  "subject": "Your order {{orderId}} is confirmed",
  "body": "Hello {{customerName}},\n\nYour order {{orderId}} worth ${{amount}} has been confirmed.\nEstimated delivery: {{deliveryDate}}\n\nThank you!",
  "enabled": true
}
```

### Template 2: Payment Failed SMS
```json
{
  "name": "Payment Failed Alert",
  "eventType": "PAYMENT_FAILED",
  "channel": "SMS",
  "subject": null,
  "body": "Payment failed for order {{orderId}}. Amount: ${{amount}}. Please update your payment method.",
  "enabled": true
}
```

### Template 3: Delivery Update Push Notification
```json
{
  "name": "Delivery Update",
  "eventType": "DELIVERY_UPDATE",
  "channel": "PUSH",
  "subject": "Your delivery is {{status}}",
  "body": "Order {{orderId}} is {{status}}. Expected at {{deliveryTime}}.",
  "enabled": true
}
```

### Template 4: Promotional Offer Email
```json
{
  "name": "Special Offer",
  "eventType": "PROMOTIONAL_OFFER",
  "channel": "EMAIL",
  "subject": "Exclusive {{discountPercent}}% off for you, {{customerName}}!",
  "body": "We have a special {{discountPercent}}% discount on {{productCategory}} items.\nUse code: {{promoCode}}\nExpires: {{expirationDate}}",
  "enabled": true
}
```

## API Endpoints

All endpoints require `X-API-KEY` header for tenant authentication and API key verification.

### Create Template
```
POST /api/v1/notification-templates
Content-Type: application/json
X-API-KEY: {tenant_api_key}

{
  "name": "Order Confirmation",
  "eventType": "ORDER_CONFIRMED",
  "channel": "EMAIL",
  "subject": "Your order {{orderId}} is confirmed",
  "body": "Hello {{customerName}}, your order worth ${{amount}} has been confirmed.",
  "enabled": true
}

Response: 201 Created
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Order Confirmation",
  "eventType": "ORDER_CONFIRMED",
  "channel": "EMAIL",
  "subject": "Your order {{orderId}} is confirmed",
  "body": "Hello {{customerName}}, your order worth ${{amount}} has been confirmed.",
  "enabled": true,
  "createdAt": "2026-11-14T13:14:00Z",
  "updatedAt": "2026-11-14T13:14:00Z"
}
```

### List Templates
```
GET /api/v1/notification-templates
X-API-KEY: {tenant_api_key}

Response: 200 OK
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "Order Confirmation",
    "eventType": "ORDER_CONFIRMED",
    "channel": "EMAIL",
    "subject": "Your order {{orderId}} is confirmed",
    "body": "Hello {{customerName}}, ...",
    "enabled": true,
    "createdAt": "2026-11-14T13:14:00Z",
    "updatedAt": "2026-11-14T13:14:00Z"
  }
]
```

### Get Template
```
GET /api/v1/notification-templates/{templateId}
X-API-KEY: {tenant_api_key}

Response: 200 OK
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Order Confirmation",
  ...
}
```

### Update Template
```
PUT /api/v1/notification-templates/{templateId}
Content-Type: application/json
X-API-KEY: {tenant_api_key}

{
  "name": "Order Confirmation V2",
  "eventType": "ORDER_CONFIRMED",
  "channel": "EMAIL",
  "subject": "Your order {{orderId}} has been confirmed!",
  "body": "Hello {{customerName}}, your order worth ${{amount}} is confirmed.",
  "enabled": true
}

Response: 200 OK
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Order Confirmation V2",
  ...
  "updatedAt": "2026-11-14T13:15:00Z"
}
```

### Enable Template
```
PATCH /api/v1/notification-templates/{templateId}/enable
X-API-KEY: {tenant_api_key}

Response: 200 OK
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "enabled": true,
  ...
}
```

### Disable Template
```
PATCH /api/v1/notification-templates/{templateId}/disable
X-API-KEY: {tenant_api_key}

Response: 200 OK
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "enabled": false,
  ...
}
```

### Delete Template
```
DELETE /api/v1/notification-templates/{templateId}
X-API-KEY: {tenant_api_key}

Response: 204 No Content
```

### Render Template
```
POST /api/v1/notification-templates/render
Content-Type: application/json
X-API-KEY: {tenant_api_key}

{
  "templateId": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "orderId": "ORD-12345",
    "customerName": "John Doe",
    "amount": "99.99"
  }
}

Response: 200 OK
{
  "subject": "Your order ORD-12345 is confirmed",
  "body": "Hello John Doe, your order worth $99.99 has been confirmed.",
  "channel": "EMAIL"
}
```

## Postman Testing Steps

### Prerequisites
1. Ensure application is running on http://localhost:8084/api
2. Have valid tenant API key from tenant registration
3. Create at least one template before testing render endpoint

### Test 1: Create Template
```
Method: POST
URL: http://localhost:8084/api/v1/notification-templates
Headers:
  X-API-KEY: {your_tenant_api_key}
  Content-Type: application/json
Body:
{
  "name": "Order Confirmation",
  "eventType": "ORDER_CONFIRMED",
  "channel": "EMAIL",
  "subject": "Your order {{orderId}} is confirmed",
  "body": "Hello {{customerName}}, your order worth ${{amount}} has been confirmed.",
  "enabled": true
}
Expected: 201 Created with template UUID in response
Save template ID for later tests
```

### Test 2: List Templates
```
Method: GET
URL: http://localhost:8084/api/v1/notification-templates
Headers:
  X-API-KEY: {your_tenant_api_key}
Expected: 200 OK with array of templates
```

### Test 3: Get Specific Template
```
Method: GET
URL: http://localhost:8084/api/v1/notification-templates/{templateId}
Headers:
  X-API-KEY: {your_tenant_api_key}
Expected: 200 OK with single template
```

### Test 4: Update Template
```
Method: PUT
URL: http://localhost:8084/api/v1/notification-templates/{templateId}
Headers:
  X-API-KEY: {your_tenant_api_key}
  Content-Type: application/json
Body:
{
  "name": "Order Confirmation V2",
  "eventType": "ORDER_CONFIRMED",
  "channel": "EMAIL",
  "subject": "Your order {{orderId}} has been confirmed!",
  "body": "Hi {{customerName}}, your order for ${{amount}} is confirmed. Thank you!",
  "enabled": true
}
Expected: 200 OK with updated template
```

### Test 5: Disable Template
```
Method: PATCH
URL: http://localhost:8084/api/v1/notification-templates/{templateId}/disable
Headers:
  X-API-KEY: {your_tenant_api_key}
Expected: 200 OK with enabled=false
```

### Test 6: Enable Template
```
Method: PATCH
URL: http://localhost:8084/api/v1/notification-templates/{templateId}/enable
Headers:
  X-API-KEY: {your_tenant_api_key}
Expected: 200 OK with enabled=true
```

### Test 7: Render Template (With Valid Payload)
```
Method: POST
URL: http://localhost:8084/api/v1/notification-templates/render
Headers:
  X-API-KEY: {your_tenant_api_key}
  Content-Type: application/json
Body:
{
  "templateId": "{templateId}",
  "payload": {
    "orderId": "ORD-12345",
    "customerName": "John Doe",
    "amount": "99.99"
  }
}
Expected: 200 OK with rendered output
```

### Test 8: Delete Template
```
Method: DELETE
URL: http://localhost:8084/api/v1/notification-templates/{templateId}
Headers:
  X-API-KEY: {your_tenant_api_key}
Expected: 204 No Content
```

## Expected Rendered Output

### Scenario 1: Standard Order Confirmation
```
Template:
{
  "subject": "Your order {{orderId}} is confirmed",
  "body": "Hello {{customerName}}, your order worth ${{amount}} has been confirmed."
}

Payload:
{
  "orderId": "ORD-98765",
  "customerName": "Jane Smith",
  "amount": "249.50"
}

Rendered:
{
  "subject": "Your order ORD-98765 is confirmed",
  "body": "Hello Jane Smith, your order worth $249.50 has been confirmed."
}
```

### Scenario 2: Multi-Variable Template
```
Template:
{
  "subject": "{{discountPercent}}% off {{productName}} for {{customerName}}",
  "body": "Use code {{promoCode}} at checkout. Valid until {{expirationDate}}."
}

Payload:
{
  "discountPercent": "25",
  "productName": "Winter Jacket",
  "customerName": "Alice",
  "promoCode": "WINTER25",
  "expirationDate": "2026-12-25"
}

Rendered:
{
  "subject": "25% off Winter Jacket for Alice",
  "body": "Use code WINTER25 at checkout. Valid until 2026-12-25."
}
```

## Error Tests

### Test: Missing Required Variable
```
Template ID: {templateId with body "Hello {{customerName}}"}
Payload: {"orderId": "ORD-123"}  // Missing customerName

Expected: 400 Bad Request
{
  "error": "Missing required variable in payload: customerName",
  "status": 400
}
```

### Test: Disabled Template Rendering
```
1. Create and disable a template
2. Try to render disabled template

Expected: 400 Bad Request
{
  "error": "Template is disabled: {templateId}",
  "status": 400
}
```

### Test: Invalid Channel
```
Method: POST
URL: http://localhost:8084/api/v1/notification-templates
Headers:
  X-API-KEY: {tenant_api_key}
Body:
{
  "name": "Invalid",
  "eventType": "ORDER_CONFIRMED",
  "channel": "TELEGRAM",
  "subject": "Test",
  "body": "Test body",
  "enabled": true
}

Expected: 400 Bad Request
{
  "error": "Invalid channel: TELEGRAM. Valid channels are: EMAIL, SMS, PUSH, IN_APP",
  "status": 400
}
```

### Test: Missing Subject for EMAIL
```
Method: POST
URL: http://localhost:8084/api/v1/notification-templates
Headers:
  X-API-KEY: {tenant_api_key}
Body:
{
  "name": "No Subject",
  "eventType": "ORDER_CONFIRMED",
  "channel": "EMAIL",
  "subject": null,
  "body": "Email body",
  "enabled": true
}

Expected: 400 Bad Request
{
  "error": "Subject is required for EMAIL templates",
  "status": 400
}
```

### Test: Duplicate Template
```
1. Create template: ORDER_CONFIRMED, EMAIL
2. Try to create another template with same: ORDER_CONFIRMED, EMAIL

Expected: 409 Conflict
{
  "error": "Template already exists for tenant, eventType=ORDER_CONFIRMED, channel=EMAIL",
  "status": 409
}
```

### Test: Tenant Isolation - Access Other Tenant's Template
```
1. Register Tenant A and create template T1
2. Register Tenant B
3. Try to GET T1 with Tenant B's API key

Expected: 404 Not Found or 403 Forbidden
{
  "error": "Template not found or access denied: {templateId}",
  "status": 404
}
```

### Test: Template Not Found
```
Method: GET
URL: http://localhost:8084/api/v1/notification-templates/{invalid-uuid}
Headers:
  X-API-KEY: {tenant_api_key}

Expected: 404 Not Found
{
  "error": "Template not found or access denied: {invalid-uuid}",
  "status": 404
}
```

## Database Schema

```sql
CREATE TABLE notification_templates (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    channel VARCHAR(50) NOT NULL,
    subject VARCHAR(500),
    body TEXT NOT NULL,
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    
    CONSTRAINT fk_template_tenant 
        FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT uq_template_tenant_event_channel 
        UNIQUE (tenant_id, event_type, channel)
);

CREATE INDEX idx_template_tenant_id ON notification_templates(tenant_id);
CREATE INDEX idx_template_tenant_event ON notification_templates(tenant_id, event_type);
CREATE INDEX idx_template_tenant_channel ON notification_templates(tenant_id, channel);
```

### Key Constraints
- **Primary Key**: UUID for distributed ID generation
- **Foreign Key**: tenant_id references tenants.id with CASCADE delete
- **Unique Constraint**: Prevents duplicate templates per (tenant, eventType, channel)
- **Indexes**: Optimized for common query patterns (list by tenant, filter by event type/channel)

## Implementation Notes

### Template Rendering Logic
The `NotificationTemplateService.renderTemplate()` method:
1. Retrieves template by ID with tenant isolation
2. Verifies template is enabled (fail-fast)
3. Uses regex pattern `\{\{\s*([a-zA-Z_][a-zA-Z0-9_]*)\s*\}\}` to find placeholders
4. For each placeholder:
   - Extracts variable name
   - Looks up value in payload map
   - Throws exception if missing
   - Escapes special regex characters before replacement
5. Returns rendered content

### Variable Naming Rules
Valid variable names must match pattern: `[a-zA-Z_][a-zA-Z0-9_]*`
- Valid: `orderId`, `customer_name`, `_amount`, `TOTAL_PRICE`
- Invalid: `123order` (starts with number), `order-id` (hyphen not allowed)

### Channel-Specific Requirements
- **EMAIL**: Both `subject` and `body` required
- **SMS**: `subject` optional (typically null), `body` required
- **PUSH**: `subject` optional, `body` required (for notification content)
- **IN_APP**: Both optional but at least `body` recommended

### Tenant Isolation Pattern
Every template query includes tenant_id filter:
- `findByIdAndTenantId()` - Verify ownership before operations
- `findByTenantId()` - List only tenant's templates
- All mutations check tenant ownership before proceeding

### Safe Substitution Approach
- Uses `Matcher.quoteReplacement()` to escape special regex characters in values
- Converts all non-string values to strings safely
- Prevents injection attacks by not evaluating expressions
- No support for filters, conditionals, or loops (intentional)

## Future Enhancements (Phase 09+)

The following are NOT implemented in Phase 08 per requirements:

1. **Complex Template Engines** - Freemarker, Velocity, Thymeleaf integration
2. **Conditional Logic** - if/else statements in templates
3. **Filters and Formatters** - Date formatting, string transformation
4. **Template Versioning** - History tracking of template changes
5. **Template Localization** - Multi-language templates
6. **Rich Text Support** - HTML templates with WYSIWYG editor
7. **Template Preview** - Before sending to users
8. **A/B Testing** - Testing multiple template variants
9. **Email Sending** - Actual email delivery (out of scope)
10. **SMS Sending** - Actual SMS delivery (out of scope)

## Git Commit Message

```
feat(phase08): notification template management and rendering

- Add NotificationTemplate entity with tenant relationship
- Implement NotificationTemplateRepository with tenant-isolated queries
- Create NotificationTemplateService with CRUD and rendering logic
- Add NotificationTemplateController with full REST endpoints
- Create database migration V004 with notification_templates table
- Add comprehensive template management endpoints:
  * POST /notification-templates (create)
  * GET /notification-templates (list)
  * GET /notification-templates/{id} (get)
  * PUT /notification-templates/{id} (update)
  * PATCH /notification-templates/{id}/enable (toggle)
  * PATCH /notification-templates/{id}/disable (toggle)
  * DELETE /notification-templates/{id} (delete)
  * POST /notification-templates/render (render with variables)
- Implement safe variable substitution using {{variable}} syntax
- Validate channels (EMAIL,SMS,PUSH,IN_APP) and requirements
- Require subject for EMAIL templates
- Prevent duplicate templates per (tenant, eventType, channel)
- Enforce tenant isolation on all operations
- Add missing variable detection with clear error messages
- Use regex-based substitution without expression evaluation
- Create DTOs: NotificationTemplateRequest, Response, TemplateRender* 
```

