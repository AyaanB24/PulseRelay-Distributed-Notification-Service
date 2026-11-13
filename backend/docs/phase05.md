# Phase 05 — Transactional Outbox and Kafka Integration

## Overview

Phase 05 implements reliable asynchronous event publishing using the **Transactional Outbox Pattern** and **Apache Kafka**. This ensures that notifications and their corresponding events are persisted atomically, preventing data loss if Kafka fails.

**Key Achievement**: Notifications and events are safely published asynchronously with guaranteed delivery semantics.

---

## Why Direct Database-Plus-Kafka Dual Writes Are Unsafe

Consider this naive approach:
```
1. Save notification to PostgreSQL
2. Publish event to Kafka
3. Return 202 Accepted
```

**Failure scenarios:**
- PostgreSQL succeeds, Kafka fails → Event is lost, notification exists
- PostgreSQL fails, Kafka succeeds → Event published but no notification
- Network partition → Application doesn't know which succeeded
- Application crash after PostgreSQL but before Kafka → Lost event

**Result**: Data inconsistency and lost events.

---

## Transactional Outbox Pattern

The Outbox Pattern ensures atomicity:

```
1. BEGIN TRANSACTION
   a. Save notification to NOTIFICATIONS table
   b. Save outbox event to OUTBOX_EVENTS table
2. COMMIT TRANSACTION
3. Separate publisher polls OUTBOX_EVENTS
4. For each PENDING event:
   a. Publish to Kafka
   b. Mark as PUBLISHED only after acknowledgement
   c. If Kafka fails, mark as FAILED and retry later
```

**Guarantees:**
- Notification and event are saved atomically (both or neither)
- If Kafka fails, the event remains in the outbox for retry
- Events are never lost (persisted before publishing)
- At-least-once delivery semantics

---

## Files Created

### Entities
- `src/main/java/com/pulserelay/entity/OutboxEvent.java` — Outbox event entity with status tracking

### DTOs
- `src/main/java/com/pulserelay/dto/OutboxEventPayload.java` — Event payload structure for Kafka messages

### Services
- `src/main/java/com/pulserelay/service/OutboxService.java` — Outbox event management
- `src/main/java/com/pulserelay/service/OutboxPublisher.java` — Scheduled publisher for Kafka integration

### Repositories
- `src/main/java/com/pulserelay/repository/OutboxEventRepository.java` — Outbox event database queries

### Configuration
- `src/main/java/com/pulserelay/config/KafkaProducerConfig.java` — Kafka producer setup

### Files Modified
- `src/main/java/com/pulserelay/service/NotificationService.java` — Now creates outbox events in same transaction
- `src/main/resources/application.yml` — Added Kafka and outbox configuration
- `pom.xml` — Added spring-kafka dependency

---

## OutboxEvent Entity

Represents a publishable event that has been saved in the database before delivery to Kafka.

| Field | Type | Description |
|-------|------|-------------|
| `id` | UUID | Unique outbox event identifier |
| `eventType` | String | Event classification (e.g., "notification.created") |
| `aggregateId` | UUID | Notification ID (links to the business entity) |
| `payload` | JSON | Event data: eventId, notificationId, tenantId, occurredAt, payload |
| `status` | Enum | PENDING, PUBLISHED, FAILED |
| `retryCount` | Integer | Number of publication attempts |
| `createdAt` | LocalDateTime | Timestamp of event creation (same transaction as notification) |
| `publishedAt` | LocalDateTime | Timestamp of successful Kafka acknowledgement (null if not published) |
| `updatedAt` | LocalDateTime | Last modification timestamp |

### Database Indexes

```sql
CREATE INDEX idx_status ON outbox_events(status);
CREATE INDEX idx_created_at ON outbox_events(created_at);
CREATE INDEX idx_aggregate_id ON outbox_events(aggregate_id);
CREATE INDEX idx_status_created ON outbox_events(status, created_at);
```

The `idx_status_created` index optimizes the publisher's query for pending events.

---

## Event Contract

Events published to Kafka follow a versioned schema:

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "eventType": "notification.created",
  "notificationId": "550e8400-e29b-41d4-a716-446655440001",
  "tenantId": "550e8400-e29b-41d4-a716-446655440002",
  "occurredAt": "2026-11-05T12:34:56.123456",
  "payload": {
    "eventType": "order_confirmation",
    "recipientEmail": "customer@example.com",
    "recipientPhone": "+1-555-0123",
    "recipientUserId": "cust_12345",
    "requestedChannels": "EMAIL,SMS",
    "priority": "HIGH",
    "payload": {
      "orderId": "ORD-2026-001",
      "amount": 150.00
    }
  },
  "version": 1
}
```

**Note**: No API keys or secrets are included in the payload.

---

## Kafka Configuration

### Docker Compose Setup (Phase 07+)

For now, Kafka can be run locally or in Docker:

```bash
docker run -d --name kafka \
  -e KAFKA_BROKER_ID=1 \
  -e KAFKA_ZOOKEEPER_CONNECT=zookeeper:2181 \
  -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://kafka:9092 \
  -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
  confluentinc/cp-kafka:7.5.0

# Create topic
docker exec kafka kafka-topics --create \
  --topic notification-events \
  --bootstrap-server localhost:9092 \
  --partitions 3 \
  --replication-factor 1
```

### Application Configuration

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      acks: all
      retries: 3
      linger-ms: 10

pulserelay:
  kafka:
    topic:
      notifications: notification-events
  outbox:
    poll-interval: 5000
```

### Environment Variables

```bash
# Kafka bootstrap servers (default: localhost:9092)
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# Notification events topic (default: notification-events)
export KAFKA_TOPIC_NOTIFICATIONS=notification-events

# Outbox publisher poll interval in milliseconds (default: 5000)
export OUTBOX_POLL_INTERVAL=5000
```

---

## OutboxPublisher

The `OutboxPublisher` is a scheduled Spring component that:

1. **Runs every 5 seconds** (configurable)
2. **Queries pending events** from the outbox_events table
3. **Publishes each event** to the Kafka topic asynchronously
4. **Waits for broker acknowledgement** (synchronous send with `get()`)
5. **Marks as PUBLISHED** only after successful acknowledgement
6. **Marks as FAILED** if Kafka returns an error (will be retried)

### Flow Diagram

```
┌──────────────────────────────────┐
│ OutboxPublisher (every 5s)       │
└────────────┬─────────────────────┘
             │
             ├─→ Query PENDING events from outbox_events
             │
             ├─→ For each event:
             │   ├─→ Build Kafka message with event data
             │   ├─→ Send to Kafka topic (synchronous)
             │   ├─→ Success? Mark PUBLISHED
             │   └─→ Failure? Mark FAILED (retry later)
             │
             └─→ Log backlog size and errors
```

### Producer Configuration

```properties
acks=all                    # Wait for all replicas (safest)
retries=3                   # Retry failed sends 3 times
linger.ms=10                # Wait 10ms before sending (batching)
enable.idempotence=true     # Prevent duplicate sends
compression.type=snappy     # Compress messages
```

---

## Transactional Behavior

### Notification Submission with Outbox

```java
@Transactional
public Notification submitNotification(Tenant tenant, String idempotencyKey, NotificationRequest request) {
    // 1. Validate input
    validateNotificationRequest(request);

    // 2. Check for duplicates (idempotency)
    if (existingNotification.isPresent()) {
        return existingNotification.get();  // No new event created
    }

    // 3. Save notification
    notification = notificationRepository.save(notification);

    // 4. Create outbox event in the SAME transaction
    outboxService.createOutboxEvent(
        notification.getId(),
        tenant.getId(),
        notification.getCreatedAt(),
        notificationData
    );

    // 5. Transaction commits:
    //    - Both notification and outbox event are persisted atomically
    //    - If either fails, both are rolled back
    return notification;
}
```

---

## Event Publishing Flow

### Scenario 1: Successful Publishing

```
1. Application receives notification request
2. Saves notification + outbox event (atomic transaction)
3. Returns 202 Accepted to client
4. Publisher (5s later) finds PENDING event
5. Publishes to Kafka
6. Kafka acknowledges
7. Publisher marks event PUBLISHED
8. (Audit trail: Event can be replayed if needed)
```

### Scenario 2: Kafka Failure

```
1. Application receives notification request
2. Saves notification + outbox event (atomic transaction)
3. Returns 202 Accepted to client
4. Publisher (5s later) finds PENDING event
5. Attempts to publish to Kafka
6. Kafka connection fails
7. Publisher catches exception, marks event FAILED
8. (5s later) Publisher finds FAILED event
9. Retries publishing (retry_count incremented)
10. Eventually succeeds and marks PUBLISHED
```

### Scenario 3: Application Crash

```
1. Application receives notification request
2. Saves notification + outbox event (transaction commits)
3. Returns 202 Accepted to client
4. Application crashes before publisher runs
5. On restart, publisher finds PENDING events
6. Publishes them to Kafka (retry)
7. No events are lost
```

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
  "organizationName": "EventTech Inc",
  "contactEmail": "admin@eventtech.com"
}
```

Save the returned `apiKey`.

---

### Step 2: Submit a Notification (Creates Outbox Event)

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
  "requestedChannels": "EMAIL",
  "priority": "HIGH",
  "payload": {
    "orderId": "ORD-2026-001",
    "amount": 150.00,
    "currency": "USD"
  }
}
```

Expected: 202 Accepted with notificationId

---

### Step 3: Verify Event Was Published to Kafka

Check application logs for:
```
Outbox event published successfully: [eventId] (aggregate: [notificationId])
```

Or use Kafka CLI tools:
```bash
docker exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic notification-events \
  --from-beginning \
  --max-messages 1
```

---

## Monitoring and Verification

### Application Logs

Monitor these log messages:

```
# Outbox event created
Outbox event created for notification: 550e8400-e29b-41d4-a716-446655440001

# Publisher finds pending events
Found 1 pending outbox events to publish

# Event published successfully
Outbox event published successfully: 550e8400-e29b-41d4-a716-446655440000 (aggregate: 550e8400-e29b-41d4-a716-446655440001)

# Failure case (retry)
Failed to publish outbox event 550e8400-e29b-41d4-a716-446655440000: [error message]
Outbox event marked as FAILED (retry count: 1): 550e8400-e29b-41d4-a716-446655440000
```

### Database Queries for Verification

```sql
-- Check pending events
SELECT id, event_type, aggregate_id, status, retry_count, created_at 
FROM outbox_events 
WHERE status = 'PENDING' 
ORDER BY created_at DESC;

-- Check published events (audit trail)
SELECT id, aggregate_id, published_at, retry_count 
FROM outbox_events 
WHERE status = 'PUBLISHED';

-- Check backlog
SELECT COUNT(*) as pending_count FROM outbox_events WHERE status = 'PENDING';
```

---

## Failure Testing

### Test 1: Stop Kafka While Publishing

```bash
# Stop Kafka broker
docker stop kafka

# Submit a notification
POST /api/v1/notifications
# (outbox event created successfully)

# Check logs - should show publishing failure
# "Failed to publish outbox event..."

# Restart Kafka
docker start kafka

# Wait 5 seconds
# Check logs - should show retry and success
# "Outbox event published successfully..."
```

### Test 2: Network Partition

Use `iptables` or Docker network isolation to simulate connection failure:

```bash
# Block traffic to Kafka
docker network disconnect bridge kafka

# Submit notification (saved in outbox)

# Verify event is FAILED in database
SELECT * FROM outbox_events WHERE status = 'FAILED';

# Restore connection
docker network connect bridge kafka

# Wait for publisher to retry
# Event should be marked PUBLISHED
```

### Test 3: Application Crash

```bash
# Start application
# Submit notification (saved in outbox_events)
# Kill Java process
kill -9 <pid>

# Check database - notification + event saved
SELECT * FROM outbox_events WHERE aggregate_id = '...';

# Restart application
# Check logs - should find PENDING events and publish them
# "Found 1 pending outbox events to publish"
# "Outbox event published successfully..."
```

---

## Architecture Decisions

### 1. Scheduled Publisher vs Event Listeners

**Chosen: Scheduled Publisher**
- Simpler to implement
- No database triggers needed
- Easier to test and debug
- Natural retry mechanism

**Alternative**: Database triggers or JPA lifecycle listeners
- More responsive
- More complex

### 2. Synchronous Kafka Send

**Chosen: Synchronous (with `send().get()`)**
- Wait for broker acknowledgement
- Only mark as PUBLISHED after Kafka confirms
- Prevents loss of events due to app crash before async callback

**Alternative**: Asynchronous with callbacks
- Higher throughput
- Harder to guarantee correct state

### 3. Event Versioning

**Events include version field** for backward compatibility
- Consumers can handle multiple versions
- Safe schema evolution
- Currently version 1

### 4. Aggregate ID (Notification ID)

**Used as Kafka message key**
- Ensures all events for a notification go to same partition
- Maintains order for a single notification
- Enables event sourcing if needed

---

## Database Schema

```sql
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_status ON outbox_events(status);
CREATE INDEX idx_created_at ON outbox_events(created_at);
CREATE INDEX idx_aggregate_id ON outbox_events(aggregate_id);
CREATE INDEX idx_status_created ON outbox_events(status, created_at);
```

---

## Security Considerations

1. **No Sensitive Data in Payload**: API keys and secrets never included in events
2. **Event Idempotency**: Each event has unique `eventId` for deduplication in consumers
3. **Tenant Isolation**: TenantId in every event for proper scoping
4. **Kafka Topic ACLs**: (Future) Restrict topic access to authorized services
5. **Payload Encryption**: (Future) Encrypt event payload if needed

---

## What's NOT Implemented (Future Phases)

- ❌ Kafka consumer/subscribers
- ❌ Event replay/audit log UI
- ❌ Metrics/monitoring dashboard
- ❌ Dead letter queue (DLQ) for permanently failed events
- ❌ Event filtering or transformation
- ❌ Consumer groups and rebalancing
- ❌ Schema registry for event versioning

---

## Git Commit Message

```
feat: implement transactional outbox pattern with Kafka integration

- Add OutboxEvent entity for persisting unpublished events
- Implement OutboxService for outbox event management
- Add OutboxPublisher scheduled service for Kafka publishing
- Update NotificationService to create outbox events in same transaction
- Add KafkaProducerConfig for reliable message publishing
- Publish notification.created events to Kafka with versioned schema
- Mark events PUBLISHED only after broker acknowledgement
- Support event retry with retry_count tracking
- Add Kafka configuration to application.yml
- Add spring-kafka dependency to pom.xml
- Prevent event data loss via Transactional Outbox Pattern
```

---

## Verification Checklist

✅ OutboxEvent entity created with status tracking  
✅ Outbox events saved in same transaction as notifications  
✅ OutboxPublisher scheduled to run every 5 seconds  
✅ Events published to Kafka with proper acknowledgement  
✅ Published events marked after broker acknowledgement  
✅ Failed events marked with retry count incremented  
✅ Event payload includes versioning  
✅ Tenant ID included in all events  
✅ No sensitive data in event payloads  
✅ Kafka connection failures handled gracefully  
✅ Application crash doesn't lose events  
✅ Database indexes optimized for publisher queries  
✅ Compiles successfully  

---

## Files Summary

| Category | File | Purpose |
|----------|------|---------|
| Entity | `OutboxEvent.java` | Event persistence |
| DTO | `OutboxEventPayload.java` | Event contract |
| Service | `OutboxService.java` | Outbox management |
| Service | `OutboxPublisher.java` | Kafka integration |
| Repository | `OutboxEventRepository.java` | Database queries |
| Config | `KafkaProducerConfig.java` | Kafka setup |
| Modified | `NotificationService.java` | Transactional outbox |
| Modified | `application.yml` | Kafka config |
| Modified | `pom.xml` | Dependencies |

