# PulseRelay

> **Reliable, distributed notification infrastructure for backend applications.**

PulseRelay is a **Spring Boot-based notification service** that centralizes Email, SMS, and Push notification delivery for backend applications.

Instead of implementing notification logic separately in every application, services can send a request to PulseRelay and let it handle **delivery, retries, idempotency, tracking, rate limiting, and channel fallback**.

---

## What PulseRelay Provides

### Reliable Notification Delivery

Centralizes notification delivery across multiple backend services through a single REST API.

**Supported channels:**

* Email
* SMS
* Push Notifications

### Idempotent Processing

Prevents duplicate notification processing using a unique **idempotency key**.

If the same notification request is received multiple times, PulseRelay identifies the existing request instead of creating another notification.

```text
Request
   ↓
Idempotency Key
   ↓
Already Exists?
 ┌───────┴───────┐
Yes              No
 ↓                ↓
Return          Create
Existing        Notification
```

### Automatic Retry

Temporary delivery failures are handled using **exponential backoff**.

```text
Attempt 1 → Failed
     ↓
Wait
     ↓
Attempt 2 → Failed
     ↓
Wait longer
     ↓
Attempt 3 → Success
```

Failed notifications are tracked through individual delivery attempts.

### Smart Channel Fallback

If the preferred channel repeatedly fails, PulseRelay can automatically switch to another configured channel.

```text
EMAIL
  ↓
Failed
  ↓
SMS
  ↓
Failed
  ↓
PUSH
```

Example:

> Email → SMS → Push

This allows the calling application to rely on PulseRelay instead of implementing its own fallback logic.

### Delivery Tracking

Every notification has a lifecycle and delivery history.

```text
PENDING
   ↓
PROCESSING
   ↓
SENT

or

PENDING
   ↓
RETRYING
   ↓
FAILED
```

Individual delivery attempts are recorded with:

* Attempt number
* Status
* Error message
* Timestamp

### API Security

PulseRelay uses **API-key authentication** for service-to-service communication.

```http
X-API-Key: <service-api-key>
```

This is designed for backend services rather than direct end-user authentication.

### Rate Limiting

Redis can be used to enforce request limits for client services and prevent excessive notification traffic.

```text
Client Service
      ↓
Redis Rate Limiter
      ↓
Allowed?
   /     \
 Yes      No
 ↓        ↓
API      429
```

---

# Architecture

```text
                    Client Services
              ┌─────────┼─────────┐
              │         │         │
          Order      Payment    Auth
          Service    Service    Service
              │         │         │
              └─────────┼─────────┘
                        ↓
                ┌───────────────┐
                │  PulseRelay   │
                │   REST API    │
                └───────┬───────┘
                        │
              ┌─────────┴─────────┐
              ↓                   ↓
          PostgreSQL             Redis
          Source of Truth      Rate Limit /
                              Cache
              │
              ↓
             Kafka
              │
              ↓
       Notification Worker
              │
       ┌──────┼──────┐
       ↓      ↓      ↓
     Email   SMS    Push
       │      │      │
       └──────┼──────┘
              ↓
       Delivery Tracking
```

---

# Example Use Case

Consider an e-commerce application.

When an order is successfully placed:

```text
User
 ↓
E-Commerce Application
 ↓
POST /api/notifications
 ↓
PulseRelay
 ↓
Kafka
 ↓
Notification Worker
 ↓
Email Provider
 ↓
User receives confirmation
```

Example request:

```http
POST /api/notifications
Content-Type: application/json
X-API-Key: <api-key>
```

```json
{
  "userId": 123,
  "templateType": "ORDER_CONFIRMATION",
  "channel": "EMAIL",
  "idempotencyKey": "order-4821-confirmation"
}
```

Response:

```json
{
  "notificationId": 781,
  "status": "PENDING"
}
```

The client can later check:

```http
GET /api/notifications/781
```

Response:

```json
{
  "notificationId": 781,
  "status": "SENT"
}
```

---

# Core Components

| Component           | Responsibility                                            |
| ------------------- | --------------------------------------------------------- |
| **Spring Boot**     | REST API and application logic                            |
| **PostgreSQL**      | Notification state, users, templates and delivery history |
| **Kafka**           | Asynchronous notification processing                      |
| **Redis**           | Rate limiting and caching                                 |
| **Spring Data JPA** | Database persistence                                      |
| **Spring Security** | API-key based service authentication                      |
| **Docker**          | Containerized deployment                                  |

---

# Database Model

```text
User
 ├── id
 ├── name
 ├── email
 ├── phone
 └── notification preferences

NotificationTemplate
 ├── id
 ├── type
 ├── subject
 └── body

Notification
 ├── id
 ├── idempotency_key (UNIQUE)
 ├── user_id
 ├── template_id
 ├── channel
 ├── status
 ├── created_at
 └── updated_at

DeliveryAttempt
 ├── id
 ├── notification_id
 ├── attempt_number
 ├── status
 ├── error_message
 └── attempted_at
```

The separation between `Notification` and `DeliveryAttempt` allows PulseRelay to maintain the **current notification state** while preserving the complete delivery history.

---

# Design Patterns

### Strategy Pattern

Different notification channels implement a common interface:

```java
NotificationChannel
       │
 ┌─────┼─────┐
 ↓     ↓     ↓
Email  SMS   Push
```

Adding another channel does not require rewriting the notification processing logic.

### Factory Pattern

Responsible for creating notification-related objects based on the requested channel and template.

### State Pattern

Controls valid notification lifecycle transitions such as:

```text
PENDING → PROCESSING → SENT
PENDING → RETRYING → FAILED
```

---

# API Overview

| Method | Endpoint                  | Purpose                      |
| ------ | ------------------------- | ---------------------------- |
| `POST` | `/api/notifications`      | Create notification          |
| `GET`  | `/api/notifications/{id}` | Get notification status      |
| `GET`  | `/api/templates`          | List notification templates  |
| `POST` | `/api/templates`          | Create notification template |

> API endpoints may evolve as development progresses.

---

# Technology Stack

**Backend**

* Java
* Spring Boot
* Spring Data JPA
* Spring Security
* Maven

**Database**

* PostgreSQL

**Messaging & Distributed Systems**

* Apache Kafka
* Redis

**DevOps**

* Docker

**Testing & API**

* Postman
* JUnit

---

# Project Structure

```text
pulserelay/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/pulserelay/
│   │   │       ├── controller/
│   │   │       ├── service/
│   │   │       ├── repository/
│   │   │       ├── model/
│   │   │       ├── dto/
│   │   │       ├── strategy/
│   │   │       └── config/
│   │   └── resources/
│   │       └── application.yml
│   │
│   └── test/
│
├── docker-compose.yml
├── Dockerfile
├── pom.xml
└── README.md
```

---

# Getting Started

## Prerequisites

Make sure you have:

* Java 17+
* Maven
* Docker
* PostgreSQL
* Kafka
* Redis

## Clone the Repository

```bash
git clone https://github.com/AyaanB24/PulseRelay.git
cd PulseRelay
```

## Run with Docker

```bash
docker compose up --build
```

## Run with Maven

```bash
mvn spring-boot:run
```

The API will be available at:

```text
http://localhost:8080
```

---

# Reliability Flow

PulseRelay is designed around reliable notification processing:

```text
API Request
    ↓
API Key Validation
    ↓
Idempotency Check
    ↓
Create Notification
    ↓
Kafka
    ↓
Worker
    ↓
Channel Handler
    ↓
Delivery
    ↓
Success?
 ┌──┴──┐
Yes    No
 ↓      ↓
SENT   Retry
         ↓
    Max Attempts?
      ┌──┴──┐
     No     Yes
      ↓      ↓
    Retry  FAILED
```

---

# Why PulseRelay?

Without a centralized notification service, individual backend applications may need to implement:

```text
Email integration
SMS integration
Push integration
Retry logic
Failure handling
Delivery tracking
Rate limiting
Duplicate prevention
```

PulseRelay moves these responsibilities into a **single reusable service**.

```text
Multiple Backend Services
          ↓
      PulseRelay
          ↓
 Reliable Notification Delivery
```

---

# Future Improvements

Planned improvements include:

* WhatsApp notification channel
* Multiple email/SMS providers
* Provider health monitoring
* Dead Letter Queue (DLQ)
* Kafka consumer scaling
* Circuit breaker for unhealthy providers
* Notification scheduling
* Advanced analytics dashboard
* Prometheus and Grafana monitoring
* Kubernetes deployment
* Distributed tracing

---

# Key Learning Areas

This project demonstrates practical understanding of:

* REST API design
* Spring Boot
* Spring Security
* Database transactions
* Idempotency
* Database constraints
* Retry strategies
* Exponential backoff
* Asynchronous processing
* Kafka
* Redis
* OOP and design patterns
* Distributed systems
* Containerization
* Service-to-service authentication

---

# Project Status

**Status:** In Development

PulseRelay is being developed incrementally, starting with the core notification API and database layer, followed by asynchronous processing, retry handling, channel fallback, security, and observability.

---

## Author

**Ayaan Bargir**

Computer Science & Engineering
Java Backend & DevOps
