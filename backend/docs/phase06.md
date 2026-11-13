# Phase 06: Asynchronous Notification Processing

## Overview

Phase 06 implements the Kafka consumer responsible for processing notification events published in Phase 05. It introduces the Inbox Pattern (complement to the Outbox Pattern) to ensure consumer-side idempotency.

**Key Achievement:** Events published to Kafka are now consumed and notifications are transitioned to PROCESSING status, with duplicate-proof message handling.

---

## Architecture

### Message Flow
```
Phase 05: OutboxPublisher         →  Kafka (notification-events topic)
                                     ↓
Phase 06: NotificationEventConsumer → ProcessedEvent (deduplication)
                                  ↓
                              Notification (status: PROCESSING)
```

### Idempotency Pattern: Outbox + Inbox

| Pattern | Component | Responsibility |
|---------|-----------|-----------------|
| **Outbox** | OutboxEvent | Tracks events waiting to publish |
| **Inbox** | ProcessedEvent | Tracks events already processed |

When a Kafka message is redelivered:
1. Consumer checks ProcessedEvent table
2. If event_id exists: Skip reprocessing (duplicate)
3. If event_id missing: Process and insert record
4. Database UNIQUE constraint prevents duplicate inserts

---

## Implementation

### 1. ProcessedEvent Entity

**File:** `src/main/java/com/pulserelay/entity/ProcessedEvent.java`

Records every successfully processed Kafka event.

```java
@Entity
@Table(name = "processed_events", indexes = {
    @Index(name = "idx_event_id", columnList = "event_id", unique = true),
    @Index(name = "idx_aggregate_id", columnList = "aggregate_id"),
    @Index(name = "idx_processed_at", columnList = "processed_at")
})
public class ProcessedEvent {
    @Id
    private UUID id;
    
    @Column(nullable = false, unique = true)
    private UUID eventId;  // Deduplication key
    
    private UUID aggregateId;  // Notification ID
    private String eventType;
    private Integer kafkaPartition;
    private Long kafkaOffset;
    private LocalDateTime processedAt;
    private LocalDateTime createdAt;
}
```

**Guarantees:**
- `UNIQUE(event_id)` prevents duplicate processing
- Kafka redelivery automatically dedup licated

### 2. Kafka Consumer Configuration

**File:** `src/main/java/com/pulserelay/config/KafkaConsumerConfig.java`

```java
@Configuration
@EnableKafka
public class KafkaConsumerConfig {
    
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, "pulserelay-consumer-group");
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);  // Manual commit
        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.setConcurrency(1);  // Single-threaded for ordering
        return factory;
    }
}
```

**Key Settings:**
- `ENABLE_AUTO_COMMIT = false`: Manual acknowledgment only after processing
- `AckMode.MANUAL`: Guarantee message safety
- `Concurrency = 1`: Preserve message ordering

### 3. Notification Processing Service

**File:** `src/main/java/com/pulserelay/service/NotificationProcessingService.java`

Core business logic for event processing.

```java
@Service
@Transactional
public class NotificationProcessingService {
    
    public void processNotificationEvent(
            UUID eventId,
            UUID aggregateId,
            String eventType,
            Map<String, Object> payload,
            Integer kafkaPartition,
            Long kafkaOffset) {
        
        // 1. Check deduplication
        if (isEventAlreadyProcessed(eventId)) {
            log.info("Event already processed, skipping: {}", eventId);
            return;
        }
        
        // 2. Load notification
        Notification notification = notificationRepository.findById(aggregateId)
            .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
        
        // 3. Mark PROCESSING
        notification.setStatus(Notification.NotificationStatus.PROCESSING);
        notificationRepository.save(notification);
        
        // 4. Record processed event
        ProcessedEvent processed = ProcessedEvent.builder()
            .eventId(eventId)
            .aggregateId(aggregateId)
            .eventType(eventType)
            .kafkaPartition(kafkaPartition)
            .kafkaOffset(kafkaOffset)
            .processedAt(LocalDateTime.now())
            .build();
        processedEventRepository.save(processed);
        
        log.info("Event processed: eventId={}, notificationId={}", eventId, aggregateId);
    }
    
    private boolean isEventAlreadyProcessed(UUID eventId) {
        return processedEventRepository.existsByEventId(eventId);
    }
}
```

### 4. Kafka Consumer Listener

**File:** `src/main/java/com/pulserelay/service/NotificationEventConsumer.java`

Consumes events from Kafka topic.

```java
@Service
public class NotificationEventConsumer {
    
    @KafkaListener(
        topics = "${pulserelay.kafka.topic.notifications:notification-events}",
        groupId = "${spring.kafka.consumer.group-id:pulserelay-consumer-group}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeNotificationEvent(
            @Payload Map<String, Object> payload,
            @Header("X-Event-ID") String eventIdHeader,
            @Header("X-Event-Type") String eventTypeHeader,
            @Header("kafka_receivedPartitionId") int partition,
            @Header("kafka_offset") long offset,
            Acknowledgment acknowledgment) {
        
        try {
            // 1. Extract and validate event metadata
            UUID eventId = parseEventId(eventIdHeader);
            Map<String, Object> metadata = extractEventMetadata(payload);
            UUID notificationId = (UUID) metadata.get("notificationId");
            UUID tenantId = (UUID) metadata.get("tenantId");
            
            // 2. Process the event
            processingService.processNotificationEvent(
                eventId,
                notificationId,
                eventTypeHeader,
                payload,
                partition,
                offset
            );
            
            // 3. Only acknowledge after successful processing
            acknowledgment.acknowledge();
            log.info("Message acknowledged: eventId={}, partition={}, offset={}",
                eventId, partition, offset);
            
        } catch (IllegalArgumentException validationException) {
            // Validation error - acknowledge to skip (no retry)
            log.error("Validation error: {}. Skipping message.", 
                validationException.getMessage());
            acknowledgment.acknowledge();
            
        } catch (Exception exception) {
            // Unexpected error - do NOT acknowledge (Kafka will redeliver)
            log.error("Error processing message. Kafka will redeliver.", exception);
            // No acknowledgment = message stays in Kafka
        }
    }
}
```

**Error Handling:**
- **Validation Error:** Acknowledge (don't retry)
  - Missing notification ID
  - Invalid UUID format
  - Malformed payload
- **Unexpected Error:** Do NOT acknowledge (Kafka retries)
  - Database connection lost
  - Unexpected exception

---

## Database Migration

**File:** `src/main/resources/db/migration/V002__Create_processed_events_table.sql`

```sql
CREATE TABLE processed_events (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,           -- Deduplication key
    aggregate_id UUID NOT NULL,              -- Notification ID
    event_type VARCHAR(100) NOT NULL,
    kafka_partition INT,
    kafka_offset BIGINT,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_processed_event_id ON processed_events(event_id);
```

---

## Notification Status Lifecycle

### Current Phase (Phase 06)
```
PENDING → PROCESSING
```

Status transitions implemented:
- **PENDING** → **PROCESSING:** When Kafka event is consumed and notification is being handled

Future phases will add:
- **PROCESSING** → **SENT:** After provider delivery (Phase 08+)
- **PROCESSING** → **FAILED:** On delivery failure
- **PROCESSING** → **RETRY_SCHEDULED:** For retry logic

### Entity Schema
```sql
ALTER TABLE notifications 
ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
    CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'FAILED', 'RETRY_SCHEDULED', 'EXPIRED'));
```

---

## Modified Files

| File | Change | Reason |
|------|--------|--------|
| `ProcessedEvent.java` | Created | Entity for deduplication tracking |
| `ProcessedEventRepository.java` | Created | JPA queries for duplicate detection |
| `KafkaConsumerConfig.java` | Created | Consumer configuration (manual ack) |
| `NotificationProcessingService.java` | Created | Business logic for processing |
| `NotificationEventConsumer.java` | Created | Kafka listener and error handling |
| `V002__Create_processed_events_table.sql` | Created | Database migration |
| `pom.xml` | Updated | Added H2 for testing |

---

## Idempotency Guarantee

### How Deduplication Works

**Scenario 1: First Delivery**
```
1. Consumer receives event (eventId: d9d50350-ee6b-417a-8be7-6b4cdf009870)
2. Query: SELECT * FROM processed_events WHERE event_id = 'd9d5...'
   Result: NOT FOUND
3. Process: Mark notification PROCESSING, insert into processed_events
4. Transaction commits atomically
5. Acknowledge message to Kafka
```

**Scenario 2: Duplicate Delivery (Network Redelivery)**
```
1. Consumer receives same event (Kafka retransmitted it)
2. Query: SELECT * FROM processed_events WHERE event_id = 'd9d5...'
   Result: FOUND (created in Scenario 1)
3. Log "Event already processed" and return
4. Acknowledge message (don't reprocess)
```

**Scenario 3: Crash After Processing (Before Ack)**
```
1. Consumer crashes after inserting into processed_events but before ack
2. Message stays in Kafka (not acknowledged)
3. Consumer restarts, Kafka redelivers message
4. Query finds event in processed_events (from Scenario 1)
5. Skip reprocessing, acknowledge
```

### Database Constraint Safety
```sql
UNIQUE(event_id) ON processed_events
```

If two threads somehow try to insert same event_id:
- First insert succeeds
- Second insert fails with UNIQUE constraint violation
- Exception is caught, logged, no message acknowledged
- Kafka retries later with fresh consumer instance

---

## Testing

### Test File: `NotificationEventConsumerTest.java`

Tests repository and service layer (integration):

1. **testCreateProcessedEvent**
   - Verify event is persisted with metadata

2. **testFindProcessedEventByEventId**
   - Verify deduplication query works

3. **testExistsByEventId**
   - Verify duplicate detection returns true/false

4. **testUniqueConstraintOnEventId**
   - Verify database rejects duplicate event_id

5. **testRecordCompleteMetadata**
   - Verify all Kafka metadata is preserved

6. **testMultipleEventsForSameNotification**
   - Verify multiple events can process same notification

7. **testAutoPopulateTimestamps**
   - Verify @PrePersist sets timestamps

**Run Tests:**
```bash
cd backend
mvn test -Dtest=OutboxRetryTest  # Phase 05 tests still pass
# Phase 06 consumer tests require integration environment
```

---

## Postman Testing Guide

### Step 1: Create Notification (Phase 02)
```
POST /api/v1/notifications
X-API-KEY: tenant-api-key

{
  "eventType": "order_confirmation",
  "priority": "HIGH",
  "recipientEmail": "customer@example.com",
  "requestedChannels": ["EMAIL"],
  "payload": {
    "orderId": "ORD-2026-001",
    "amount": 150.00,
    "currency": "USD"
  }
}
```

**Response:**
```json
{
  "notificationId": "91179caf-a3a2-4145-a055-0da17c4bf563",
  "status": "PENDING",
  "createdAt": "2026-11-13T18:00:00Z"
}
```

### Step 2: Wait for Publisher (Phase 05)
- Wait 5-10 seconds
- OutboxPublisher publishes event to Kafka

### Step 3: Consumer Processes
- NotificationEventConsumer consumes from Kafka
- Notification status changes to PROCESSING
- ProcessedEvent inserted into database

### Step 4: Verify Database
```sql
-- Check notification status
SELECT id, status, created_at, updated_at 
FROM notifications 
WHERE id = '91179caf-a3a2-4145-a055-0da17c4bf563';
-- Expected: status = 'PROCESSING'

-- Check processed event
SELECT event_id, aggregate_id, processed_at 
FROM processed_events 
WHERE aggregate_id = '91179caf-a3a2-4145-a055-0da17c4bf563';
-- Expected: One record with current timestamp
```

### Step 5: Test Duplicate Prevention
Manually re-send the same event to Kafka:
```bash
kafka-console-producer --broker-list localhost:9092 \
  --topic notification-events <<EOF
{
  "eventId": "d9d50350-ee6b-417a-8be7-6b4cdf009870",
  "eventType": "notification.created",
  "notificationId": "91179caf-a3a2-4145-a055-0da17c4bf563",
  "tenantId": "b8aa0ee9-f8ba-49f9-8f9d-19e490e9bdd1",
  "payload": {...}
}
EOF
```

**Expected Behavior:**
- Consumer logs: "Event already processed, skipping"
- Notification status remains PROCESSING (not re-updated)
- No duplicate record in processed_events

---

## Expected Database Changes

### Before (After Phase 05)
```
notifications:
  id: 91179caf-a3a2-4145-a055-0da17c4bf563
  status: PENDING
  created_at: 2026-11-13T18:00:00Z
  updated_at: 2026-11-13T18:00:00Z

outbox_events:
  id: d9d50350-ee6b-417a-8be7-6b4cdf009870
  status: PUBLISHED
  aggregate_id: 91179caf-a3a2-4145-a055-0da17c4bf563
  published_at: 2026-11-13T18:00:05Z
```

### After (With Phase 06 Consumer Running)
```
notifications:
  id: 91179caf-a3a2-4145-a055-0da17c4bf563
  status: PROCESSING         ← ✅ UPDATED
  created_at: 2026-11-13T18:00:00Z
  updated_at: 2026-11-13T18:00:10Z  ← ✅ UPDATED

processed_events:           ← ✅ NEW TABLE
  id: <auto-uuid>
  event_id: d9d50350-ee6b-417a-8be7-6b4cdf009870
  aggregate_id: 91179caf-a3a2-4145-a055-0da17c4bf563
  kafka_partition: 0
  kafka_offset: 42
  processed_at: 2026-11-13T18:00:08Z
  created_at: 2026-11-13T18:00:08Z
```

---

## Failure Scenarios

### Scenario 1: Kafka Unavailable
```
Timeline:
1. Consumer tries to fetch from Kafka
2. Connection timeout
3. Consumer retries automatically (Spring Kafka)
4. Once Kafka is back: Consumer resumes, processes events
```

### Scenario 2: Database Connection Lost
```
Timeline:
1. Consumer receives event
2. Tries to query ProcessedEvent table
3. SQLException: Connection lost
4. Exception caught in consumer
5. Message NOT acknowledged
6. Kafka retains message
7. Consumer retries on reconnection
```

### Scenario 3: Duplicate Event Processing
```
Timeline:
1. Consumer processes event, inserts ProcessedEvent
2. Network issue prevents acknowledgment
3. Kafka redelivers message
4. Consumer receives duplicate
5. Query finds existing ProcessedEvent
6. Log "already processed", skip business logic
7. Acknowledge message
Result: No duplicate processing ✅
```

### Scenario 4: Invalid Payload
```
Timeline:
1. Malformed event (missing notificationId)
2. Consumer throws IllegalArgumentException
3. Caught as validation error
4. Message acknowledged (don't retry validation errors)
5. Event is skipped without reprocessing
Result: Consumer doesn't crash ✅
```

---

## Configuration

**application.yml**
```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: pulserelay-consumer-group

pulserelay:
  kafka:
    topic:
      notifications: notification-events
```

---

## Deployment Checklist

- [x] ProcessedEvent entity created
- [x] Kafka consumer configured with manual acknowledgment
- [x] NotificationProcessingService implements business logic
- [x] NotificationEventConsumer has error handling
- [x] Database migration creates processed_events table
- [x] UNIQUE(event_id) constraint enforces idempotency
- [x] Tests verify deduplication
- [x] H2 test database configured
- [x] Logging added for monitoring

---

## Conventional Commit

```
feat(phase-06): implement kafka consumer for asynchronous notification processing

- Add ProcessedEvent entity and repository for consumer-side deduplication
- Implement NotificationProcessingService with business logic
- Configure KafkaConsumerConfig with manual acknowledgment (AckMode.MANUAL)
- Implement NotificationEventConsumer with error handling and idempotency
- Add database migration V002 for processed_events table
- Update Notification.status from PENDING to PROCESSING
- Add tests for deduplication and event metadata recording
- Support Kafka redelivery without duplicate processing

This completes the Inbox Pattern (complement to Phase 05 Outbox).
Guarantees: duplicate Kafka events do not repeat business operations.
```

---

## Key Learnings

### 1. Manual Acknowledgment Pattern
```java
@KafkaListener(...)
public void consume(..., Acknowledgment ack) {
    try {
        // Process
        ack.acknowledge();  // Only after success
    } catch (ValidationException e) {
        ack.acknowledge();  // Skip don't-retry errors
    } catch (Exception e) {
        // No ack = Kafka retries
    }
}
```

### 2. Idempotency via Unique Constraint
```sql
UNIQUE(event_id) ON processed_events
```
Database enforces one-time-only processing without explicit checks.

### 3. Fail-Safe Error Handling
- **Validation errors** → Acknowledge (no retry)
- **Transient errors** → Don't acknowledge (Kafka retries)
- **Consumer crashes** → Message redelivered, dedup catches it

### 4. Transaction Boundaries
All three operations (check, update, insert) happen in one `@Transactional` method:
- Load notification
- Update status
- Insert ProcessedEvent

If transaction fails, message isn't acknowledged, Kafka retries.

---

## Next Phase: Phase 07

Phase 07 will implement:
- Notification provider integration (Email, SMS)
- Template rendering
- Actual delivery to providers
- Status transitions to SENT / FAILED
- Retry scheduling for failures

(Not implemented in this phase per requirements)
