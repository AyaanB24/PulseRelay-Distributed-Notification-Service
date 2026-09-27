# PulseRelay

**A developer-facing notification orchestration platform.** Applications send a single API request; PulseRelay handles channel routing, templating, retries, rate limiting, provider failover, and delivery tracking across Email, SMS, and Push.

[![Java](https://img.shields.io/badge/Java-17-orange)]()
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen)]()
[![Kafka](https://img.shields.io/badge/Kafka-Event%20Streaming-black)]()
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Database-blue)]()
[![Redis](https://img.shields.io/badge/Redis-Rate%20Limiting-red)]()
[![Docker](https://img.shields.io/badge/Docker-Containerized-2496ED)]()
[![License](https://img.shields.io/badge/license-MIT-lightgrey)]()

---

## Overview

Applications that need to notify their users typically end up calling email, SMS, and push providers directly from multiple services — duplicating provider integrations, retry logic, and delivery tracking everywhere they're needed. When a provider goes down or a template needs to change, every caller has to be touched.

PulseRelay removes that duplication. A client application sends one authenticated request describing *what happened* (`ORDER_CONFIRMED`, `PAYMENT_FAILED`, etc.); PulseRelay decides *how* to deliver it — which channel, which provider, which template — and guarantees the notification is either delivered or provably failed, with a full audit trail.

```
Client App  →  PulseRelay API  →  Kafka  →  Notification Worker  →  Provider  →  End User
                     │
                     └──→ PostgreSQL (state) · Redis (rate limits) · Prometheus (metrics)
```

---

## Table of Contents

- [Why PulseRelay](#why-pulserelay)
- [Architecture](#architecture)
- [System Flow](#system-flow)
- [Core Features](#core-features)
- [Tech Stack](#tech-stack)
- [Getting Started](#getting-started)
- [API Reference](#api-reference)
- [Reliability Design](#reliability-design)
- [Data Model](#data-model)
- [Observability](#observability)
- [Roadmap](#roadmap)
- [Project Status](#project-status)

---

## Why PulseRelay

| Without PulseRelay | With PulseRelay |
|---|---|
| Every service integrates its own email/SMS/push SDKs | One REST API, one API key |
| Retry and failover logic duplicated per service | Centralized retry, backoff, and provider failover |
| No unified view of what was sent or why it failed | Full delivery history and per-attempt audit log |
| Hardcoded message strings in application code | Reusable, versioned templates |
| Adding a channel means changing every caller | Adding a channel means configuring PulseRelay |

PulseRelay's core differentiator is not "sending notifications" — that's commodity functionality any SDK provides. The value is **policy-driven delivery with automatic channel and provider failover**, so the calling application never has to know or care that a provider is degraded.

---

## Architecture

```mermaid
flowchart TB
    Client["Client Application"] -->|"POST /notifications"| API["PulseRelay API<br/>(Auth + Validation)"]
    Dash["Developer Dashboard"] -->|"GET /notifications"| API
    API -->|"202 Accepted"| Client

    API -->|"write QUEUED"| DB[("PostgreSQL")]
    API -->|"publish event"| Kafka["Kafka<br/>notification.events"]

    Kafka --> W1

    subgraph Worker["Worker Pool"]
        direction TB
        W1["1 . Idempotency Check"] --> W2["2 . Preference Lookup"]
        W2 --> W3["3 . Delivery Decision Engine<br/>preference + priority + provider health"]
        W3 --> W4["4 . Template Rendering"]
        W4 --> W5["5 . Rate Limit Check"]
    end

    W1 -.->|"idempotency_key lookup"| DB
    W2 -.->|"read preferences"| DB
    W5 -->|"check / increment"| Redis[("Redis")]

    W5 --> Channel["Notification Channel<br/>provider abstraction"]
    Channel --> SendGrid["SendGrid — Email"]
    Channel --> Twilio["Twilio — SMS"]
    Channel --> FCM["FCM — Push"]

    SendGrid --> Result["Delivery Result"]
    Twilio --> Result
    FCM --> Result

    Result -->|"update status +<br/>log attempt"| DB
    Result -->|"on failure"| Retry["Retry + Backoff"]
    Retry -->|"attempt again"| Channel
    Retry -->|"max attempts reached"| DLQ["Kafka DLQ"]

    API --> Prom["Prometheus"]
    Worker --> Prom
    Prom --> Grafana["Grafana"]
    Dash -->|"view metrics"| Grafana
```

**Why it's built this way:**
- **Idempotency runs first**, before any preference lookup or routing work, checked against a `UNIQUE` constraint on `idempotency_key` in PostgreSQL — that's the final authority, so a Kafka redelivery can never slip through as a duplicate.
- **The Delivery Decision Engine** — not a plain routing lookup — weighs user preference, event priority, and live provider health together to pick a channel *and* a provider. That's the actual product feature; it's what makes automatic failover possible.
- **Every provider call goes through a `NotificationChannel` abstraction**, not a direct SDK call from the worker. Adding a provider or failing over from one to another means adding an implementation, not rewriting the worker.
- **Retry is explicit and bounded**: each failure is retried with backoff and a capped attempt count, with every attempt logged — only once attempts are exhausted does a message move to the DLQ.
- **Redis and PostgreSQL have distinct jobs**: Redis holds fast, ephemeral state (rate-limit counters, idempotency fast-path); PostgreSQL holds durable state (notification status, full delivery-attempt history). Rate limiting is never backed by Postgres.
- **The dashboard never touches Kafka, Redis, or providers directly** — it reads exclusively through the API, which queries PostgreSQL for status and attempt history, so it can't drift out of sync with what actually happened.

---

## System Flow

**1. Integration setup**
A client creates an organization, generates an API key, and configures which channels/providers are enabled.

**2. Sending a notification**
```http
POST /api/v1/notifications
Authorization: Bearer pr_live_xxxxxxxxxxxxx
Content-Type: application/json

{
  "event": "ORDER_CONFIRMED",
  "userId": "user_123",
  "data": { "orderId": "ORD-4821", "amount": 2499 }
}
```
PulseRelay responds immediately without waiting for delivery:
```json
{ "notificationId": "ntf_10231", "status": "QUEUED" }
```

**3. Routing decision**
The Notification Engine resolves the user's channel preferences and the event's delivery policy, then selects a channel and a healthy provider.

**4. Template rendering**
The event payload (`orderId`, `amount`, etc.) is merged into a stored, versioned template — the client never hardcodes message copy.

**5. Delivery + retry**
A worker calls the provider SDK. On failure, PulseRelay retries with exponential backoff; on repeated failure it can fail over to a secondary provider or fall back to another channel entirely (e.g., Email → SMS) before landing in the DLQ.

**6. Tracking**
Every attempt — provider used, response, latency, outcome — is written to PostgreSQL and visible in the dashboard, from `QUEUED → PROCESSING → RETRYING → DELIVERED` (or `FAILED`).

---

## Core Features

- **Single API, multi-channel delivery** — Email, SMS, and Push behind one endpoint
- **Policy-driven routing** — per-event delivery policy plus per-user channel preferences
- **Provider abstraction & failover** — swap or add providers without client-side changes
- **Templating** — versioned, variable-driven message templates per event type
- **Retry with exponential backoff** — configurable per channel/provider
- **Dead-letter queue** — failed notifications are preserved, not dropped
- **Idempotency** — safe against duplicate event delivery from the client side
- **Rate limiting** — per-organization request throttling via Redis, protecting both PulseRelay and upstream providers
- **Delivery history & audit trail** — every attempt, per notification, queryable
- **Developer dashboard** — send volume, delivery/failure rates, DLQ inspection, per-notification drill-down

---

## Tech Stack

| Layer | Technology |
|---|---|
| API / backend | Java 17, Spring Boot 3, Spring Security (API-key auth) |
| Event streaming | Apache Kafka |
| Database | PostgreSQL |
| Caching / rate limiting | Redis |
| Providers | SendGrid (Email), Twilio (SMS), Firebase Cloud Messaging (Push) |
| Frontend | React |
| Observability | Prometheus, Grafana |
| Infrastructure | Docker, Docker Compose; CI via GitHub Actions |

---

## Getting Started

### Prerequisites
- Docker & Docker Compose
- Java 17+
- A SendGrid API key (email) and/or Twilio trial credentials (SMS) for live delivery testing

### Run locally
```bash
git clone https://github.com/AyaanB24/pulserelay.git
cd pulserelay
cp .env.example .env        # add your provider API keys
docker compose up -d        # Kafka, Redis, PostgreSQL, app
```

The API is available at `http://localhost:8080`, and the dashboard at `http://localhost:3000`.

### Send a test notification
```bash
curl -X POST http://localhost:8080/api/v1/notifications \
  -H "Authorization: Bearer pr_live_xxxxxxxxxxxxx" \
  -H "Content-Type: application/json" \
  -d '{
        "event": "ORDER_CONFIRMED",
        "userId": "user_123",
        "data": { "orderId": "ORD-4821", "amount": 2499 }
      }'
```

---

## API Reference

| Endpoint | Method | Description |
|---|---|---|
| `/api/v1/notifications` | `POST` | Submit a notification event |
| `/api/v1/notifications/{id}` | `GET` | Get status and delivery attempt history |
| `/api/v1/templates` | `POST` / `GET` | Create or list message templates |
| `/api/v1/users/{id}/preferences` | `PUT` / `GET` | Set or fetch a user's channel preferences |
| `/api/v1/api-keys` | `POST` | Issue a new API key for an organization |

Full request/response schemas are documented in [`/docs/api.md`](./docs/api.md).

---

## Reliability Design

**Retry + failover example (payment failure, high priority):**
```
PAYMENT_FAILED
   → priority: HIGH
   → user preference: SMS
   → Provider A: unhealthy
   → Provider B: attempted
   → Provider B: FAILED
   → channel fallback: PUSH
   → DELIVERED
```

- **Retries** use exponential backoff (e.g., 2s → 4s → 8s → 16s), tracked in Redis so retry state survives worker restarts. Each attempt is logged as a `DeliveryAttempt` row before the next one runs.
- **Provider failover** switches to a secondary provider within the same channel before giving up on that channel.
- **Channel fallback** (e.g., Email → SMS) is policy-configurable per event type.
- **Dead-letter queue** captures anything that exhausts every retry and fallback path, so nothing is silently lost.
- **Idempotency keys**, enforced with a `UNIQUE` constraint on `notification.idempotency_key`, stop PulseRelay from reprocessing the same inbound event twice (e.g., on Kafka redelivery).

**A known, honest limitation:** idempotency at the database layer prevents PulseRelay from reprocessing its own event twice — it cannot guarantee a provider never delivers a message twice. If a provider sends an email successfully but the network response to PulseRelay is lost, PulseRelay may see that as a failure and retry, causing a duplicate at the provider. This is a real constraint of any system that crosses a network boundary it doesn't control. The accurate claim is: *PulseRelay provides idempotent processing and minimizes duplicate delivery through database constraints, provider-side idempotency where supported, and controlled retries* — not a hard guarantee of exactly-once delivery end to end.

---

## Data Model

PostgreSQL is the durable source of truth for:

```
Organizations
API Keys
Users
User Preferences
Templates
Notifications
Delivery Attempts
Provider Configurations
Usage Metrics
```

A single notification's lifecycle:
```
QUEUED → PROCESSING → RETRYING → DELIVERED
                    └──────────→ FAILED → DLQ
```

Core tables, simplified:
```
Notification
────────────────────
id
idempotency_key      UNIQUE
user_id
channel
status                -- QUEUED | PROCESSING | RETRYING | DELIVERED | FAILED
provider
created_at
updated_at

DeliveryAttempt
────────────────────
id
notification_id       -- FK → Notification
attempt_number
provider
status
error_message
attempted_at
```
`Notification` holds current state; `DeliveryAttempt` holds one row per try, which is what makes the dashboard's per-notification drill-down (`ntf_1023 → attempt #1 timeout, #2 rate limited, #3 rejected`) possible instead of just a final status.

---

## Observability

- **Prometheus** scrapes queue lag, delivery success/failure rate, and per-provider latency.
- **Grafana** dashboards visualize throughput, failure trends, and DLQ growth.
- The developer dashboard surfaces the same data in product form: send volume, delivery rate, and per-notification attempt logs.

---

## Roadmap

**Built / in progress**
- [x] Multi-tenant organizations & API-key auth
- [x] REST API + Kafka-backed async processing
- [x] Email delivery via SendGrid (real inbox delivery)
- [x] Templates, user preferences, idempotency
- [x] Retry with exponential backoff
- [x] Delivery history & React dashboard
- [x] Docker Compose local deployment

**Planned**
- [ ] SMS (Twilio) and Push (FCM) channels
- [ ] Provider health checks & automatic failover
- [ ] Channel fallback policies
- [ ] Outbound webhooks for delivery events
- [ ] Usage-based billing
- [ ] Production deployment (AWS)

---




## License

Private - All rights reserved
