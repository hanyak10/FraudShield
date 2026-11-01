# 🛡️ FraudShield


A full-stack, demo-grade **real-time fraud detection platform**. A payment event comes in, a
weighted rules engine scores it in milliseconds, and anything suspicious is flagged for an
analyst to review — the same shape as the fraud systems behind banks and payment processors,
but with all data mocked and no real money involved.

> **Not an ML/AI system.** Fraud scoring is a deterministic, weighted **rules engine** (fixed
> thresholds and stable hashes), not a machine-learning model.

> 📖 **New here? Start with the hosted guide:** **https://hanyak10.github.io/FraudShield/** —
> a complete walkthrough of the architecture, services, API, security, and Eureka setup.

---

## Repository layout

```
fraudshield/
├── backend/                 Spring Boot 3.2 monolith (Java 17, H2, JWT, Eureka client)
├── UI/                      React 18 + Vite analyst dashboard
├── Postman/                 Importable Postman collection (auto-manages tokens)
├── api-request-bodies.json  Postman-ready request-body reference for every endpoint
└── README.md                You are here
```

| Part | Stack | Runs on | Needs |
|------|-------|---------|-------|
| **Backend** | Spring Boot 3.2, Spring Security 6, Spring Data JPA, H2, JWT (jjwt), Eureka client, springdoc | `:8080` | Java 17, Maven 3.9+ |
| **UI** | React 18, Vite 5, React Router 6 | `:5173` | Node 18+, npm |
| **Eureka server** *(optional)* | Netflix Eureka | `:8761` | only if `EUREKA_ENABLED=true` |

---

## How the project works

### The big picture

FraudShield has two running pieces: a **React analyst dashboard** and a **Spring Boot backend**.
The dashboard is where a fraud analyst logs in, watches transactions stream in, inspects why each
one was flagged, overrides verdicts, and tunes the detection rules. The backend is the brain —
it authenticates users, scores every transaction against the fraud rules, records an immutable
audit trail, and serves all of that over a REST API.

```
┌────────────┐   REST + JWT Bearer   ┌───────────────────────────────┐   (optional)
│  React UI  │ ────────────────────▶ │       FraudShield backend       │ ─register─▶ Eureka
│   :5173    │ ◀──────────────────── │  Spring Boot monolith  :8080     │              :8761
└────────────┘      JSON responses   │                                 │
                                      │  Controllers → Services → JPA   │
                                      │              │                  │
                                      │        H2 in-memory DB          │
                                      └───────────────────────────────┘
```

> **Design vs. reality.** The hosted guide documents a *target* 6-microservice architecture
> (Transaction, Rules Engine, Alert, Admin API, Auth, Notification Worker). What actually ships
> here is a **single Spring Boot application** — a modular monolith — that folds those
> capabilities into **5 controllers** and **7 service beans**. This README describes the real code.

### Layers inside the backend

Every request flows through the same four layers:

```
HTTP ─▶ JwtAuthenticationFilter ─▶ Controller ─▶ Service ─▶ Repository ─▶ H2
                                       │             │
                                    (DTOs in)    (entities)
```

- **`JwtAuthenticationFilter`** — runs once per request. If an `Authorization: Bearer <token>`
  header is present, it validates the JWT and populates Spring Security's context with the user's
  email and role. Invalid/expired tokens are swallowed silently so the request continues
  unauthenticated and the security layer returns `401`.
- **Controllers** (`Auth`, `Transaction`, `Admin`, `Rule`, `Config`) — map HTTP endpoints,
  accept/return DTOs, and read the current user from the security context.
- **Services** (`Auth`, `Jwt`, `CustomUserDetails`, `Transaction`, `RulesEngine`, `Rule`,
  `Audit`) — hold all business logic. Entities never leave this layer.
- **Repositories** — Spring Data JPA over an H2 in-memory database (PostgreSQL-ready for prod).

### What happens when a transaction comes in

A `POST /api/transactions` is the core path. Step by step:

1. **Authenticate.** `JwtAuthenticationFilter` validates the Bearer token and sets the security
   context; the controller reads the caller's email as the `userId`.
2. **Build the entity.** `TransactionService.create()` generates a `tx-<n>` id, normalizes the
   currency, and derives a country from the optional latitude/longitude.
3. **Score it.** `RulesEngineService.score()` loads the **enabled** fraud rules and sums the
   weight of every rule that fires.
4. **Decide.** `score >= 50` → verdict `FLAGGED`, status `PENDING`; otherwise `PASSED`. The fired
   rule ids are stored on the transaction.
5. **Persist + audit.** The transaction is saved, `AuditService.log()` appends an audit entry,
   and the API returns **`202 Accepted`** with `{transactionId, status, createdAt}`.

### How scoring actually works

The engine is deterministic — the same input always produces the same score, which keeps the
demo reproducible. `RulesEngineService` evaluates each enabled rule:

| id | name | weight | enabled | fires when |
|----|------|--------|---------|------------|
| `velocity` | Velocity Check | 40 | ✅ | stable hash bucket of `id + merchantId` |
| `amount` | Amount Anomaly | 30 | ✅ | amount > 2000 |
| `geo` | Geo-Mismatch | 20 | ✅ | stable hash bucket of `id + merchantId` |
| `duplicate` | Duplicate Detection | 10 | ✅ | stable hash bucket of `id + merchantId` |
| `nightowl` | Odd-Hour Spending | 15 | ❌ | amount > 1000 and hour 02:00–05:00 UTC |

- `amount` and `nightowl` are true threshold checks on the transaction's own fields.
- `velocity`, `geo`, and `duplicate` fire via `bucket()` — a stable hash of `id + merchantId`
  (plus a per-rule salt) mapped to 0–99 — so a given transaction always triggers the same rules.
- Only **enabled** rules contribute. Toggling a rule or changing its weight (via the Rules API)
  immediately changes future scores; existing verdicts are not re-computed.

### Authentication & token flow

Auth is **stateless JWT** — no server-side sessions.

1. `POST /api/auth/login` verifies the BCrypt password (strength 12) and issues two tokens:
   - an **access token** — signed HS256 JWT carrying `subject` (email), `role`, and `name`
     claims, **15 min TTL**;
   - a **refresh token** — a 256-bit random string, **7 day TTL**. Only its **SHA-256 hash** is
     stored in the database, never the raw value.
2. The client sends the access token on every protected call as `Authorization: Bearer <token>`.
3. `POST /api/auth/refresh` rotates the pair: the old refresh token is marked **used** (single
   use) and a fresh access+refresh pair is returned.
4. `POST /api/auth/logout` marks all of the user's active refresh tokens used.

Roles are enforced with method-level checks — `ROLE_ANALYST` can review and override verdicts;
rule writes require `ROLE_ADMIN`/`ROLE_SUPER_ADMIN` (an analyst gets `403`).

### Audit trail

Nearly every write path calls `AuditService.log(actor, action, ip, icon)`, appending an immutable
row (who did what, from which IP, when). The client IP is resolved from the first `X-Forwarded-For`
hop when present, else the remote address. Analysts/admins read the trail via
`GET /api/admin/audit-logs`.

### Data seeding (fresh every run)

H2 is in-memory, so the database starts empty on every boot. `DataInitializer` (a
`CommandLineRunner`) seeds a reproducible dataset once, using a fixed random seed (`42`):

- **3 users** (admin, analyst, super-admin — see the table below).
- **5 fraud rules** (the table above; `nightowl` seeded disabled).
- **48 transactions** — a mix of high/low amounts so some trigger the amount rule; ~30% of flagged
  ones are marked `OVERRIDDEN` for a realistic spread.
- **6 audit-log entries**.

Seeded rows carry an internal `seeded=true` flag, which powers mock vs. real mode (below).

---

## Quick start

Run the backend and UI in separate terminals. Eureka is optional.

### 1. Backend — `:8080`

```bash
cd backend
mvn spring-boot:run
```

Starts on **http://localhost:8080** with a fresh, seeded H2 database every restart.

- Swagger UI — http://localhost:8080/swagger-ui.html
- H2 console — http://localhost:8080/h2-console (JDBC `jdbc:h2:mem:fraudshield`, user `sa`, blank password)
- Health — http://localhost:8080/actuator/health

Running the backend alone without a registry? Disable Eureka to avoid connection-refused retries:

```bash
# PowerShell
$env:EUREKA_ENABLED="false"; mvn spring-boot:run
# bash
EUREKA_ENABLED=false mvn spring-boot:run
```

### 2. UI — `:5173`

```bash
cd UI
npm install
npm run dev
```

Opens at **http://localhost:5173**. The backend enables CORS for this origin.

### 3. Eureka server (optional)

The backend is a Eureka **client**; it expects a registry at `http://localhost:8761/eureka/`.
Start any standard Eureka server on `:8761` first, or set `EUREKA_ENABLED=false` to skip
discovery entirely.

---

## Demo accounts (seeded)

| Email | Password | Role | Can do |
|-------|----------|------|--------|
| `HKadmin@fraudshield.io` | `Admin@123` | `ROLE_ADMIN` | Everything, incl. toggling rules |
| `HKanalyst@fraudshield.io` | `Analyst@123` | `ROLE_ANALYST` | Review + override verdicts |
| `HKsuperadmin@fraudshield.io` | `SuperAdmin@123` | `ROLE_SUPER_ADMIN` | Everything |

Passwords are stored as BCrypt hashes (strength 12).

---

## REST API

All paths are prefixed with `/api`. Full request bodies, field types, and validation rules live
in **[`api-request-bodies.json`](api-request-bodies.json)** (Postman-ready).

### Using the Postman collection

An importable collection lives at **`Postman/FraudShield.postman_collection.json`**. It groups
every endpoint into folders (Auth, Transactions, Admin, Fraud Rules, Config) and wires up auth
for you.

1. In Postman, **Import** → select `Postman/FraudShield.postman_collection.json`.
2. Start the backend (`:8080`). The collection's `baseUrl` variable already points there.
3. Run **`Auth → 1. Login (HK Admin)`** first. A test script auto-saves `accessToken` and
   `refreshToken` into collection variables.
4. Run any other request — the collection applies `Authorization: Bearer {{accessToken}}`
   automatically, so you never paste a token by hand.
5. Create-transaction requests save the new id into `{{lastTxId}}`, so the override-verdict and
   get-by-id requests target it with no edits.

The backend runs in **real** mode by default, so UI panels start empty — data you create through
the collection shows up live in the UI at `http://localhost:5173`. Add an `X-Mock-Response: true`
header on any list request to force seeded mock data instead.

### Auth — `/api/auth`
| Method | Path | Auth | Notes |
|--------|------|------|-------|
| POST | `/login` | public | `{email,password}` → token pair + user |
| POST | `/refresh` | public | `{refreshToken}` → new token pair |
| POST | `/logout` | Bearer | 204; revokes the user's refresh tokens |
| GET | `/me` | Bearer | `{name,email,role}` |

### Transactions — `/api/transactions`
| Method | Path | Auth | Notes |
|--------|------|------|-------|
| GET | `/` | authenticated | list; optional `q`, `verdict`, `status` |
| GET | `/{id}` | authenticated | one transaction with triggered rules |
| POST | `/` | authenticated | create + score; **202** `{transactionId,status,createdAt}` |

### Admin — `/api/admin` (ROLE_ANALYST and above)
| Method | Path | Auth | Notes |
|--------|------|------|-------|
| GET | `/transactions` | ANALYST+ | analyst transaction list |
| PUT | `/verdicts/{id}` | ANALYST+ | `{decision:"FALSE_POSITIVE"\|"CONFIRMED_FRAUD"}` |
| GET | `/audit-logs` | ADMIN+ | audit entries, newest first |

### Rules — `/api/admin/rules`
| Method | Path | Auth | Notes |
|--------|------|------|-------|
| GET | `/` | ANALYST+ | list rules |
| PUT | `/{id}` | ADMIN+ | `{enabled?,weight?}`; analyst gets 403 |

### Config — `/api/config`
| Method | Path | Auth | Notes |
|--------|------|------|-------|
| GET | `/mock-status` | public | `{mockEnabled,source}` for the current request |

---

## Mock vs. real data

The backend serves either **seeded demo data** (mock) or only **runtime-created data** (real),
resolved per request:

1. Header **`X-Mock-Response: true|false`** wins when present.
2. Otherwise the config key **`fraudshield.mock.enabled`** in `application.yml` (default `false`).

Only rows with `seeded=true` are the demo data; anything created at runtime is `seeded=false`. In
**mock** mode the list endpoints return everything; in **real** mode they return only the
runtime rows. Affects `GET /api/transactions`, `GET /api/admin/transactions`, and
`GET /api/admin/audit-logs`. Rules are never mode-filtered and rule writes persist in both modes.

```bash
# force seeded data regardless of config
curl -H "X-Mock-Response: true" http://localhost:8080/api/transactions
```

---

## Service discovery (Eureka)

The backend registers with a Netflix Eureka server via `spring-cloud-starter-netflix-eureka-client`
(Spring Cloud 2023.0.1), configured in `backend/src/main/resources/application.yml`.

| Variable | Default | Effect |
|----------|---------|--------|
| `EUREKA_ENABLED` | `true` | `false` runs the backend standalone (no registration or lookup) |
| `EUREKA_URL` | `http://localhost:8761/eureka/` | points the client at a different Eureka server |

Registers as `fraudshield-backend:8080`, by IP, with ~30s heartbeats, and de-registers on
graceful shutdown.

---

## Errors

Structured JSON, no stack traces: `{timestamp,status,error,message,path}`. Validation failures
return 400 with a `fieldErrors` map. 401 for missing/invalid auth, 403 for insufficient role.

---

## Further reading

- **[Hosted architecture guide](https://hanyak10.github.io/FraudShield/)** — the full walkthrough:
  HLD, the service design, the backend code deep-dive, Eureka, security, and the UI reference.
- **`backend/README.md`** — backend-specific run notes and endpoint details.
- **`UI/README.md`** — frontend features, structure, and build.
- **`Postman/FraudShield.postman_collection.json`** — importable collection, auto-manages tokens.
- **`api-request-bodies.json`** — copy-paste request bodies for Postman.

---

## Notes

- All data is mocked; there is no real money or PII. The seeded JWT secret in `application.yml`
  is **dev-only** — override `jwt.secret` in any non-dev environment.
- H2 is in-memory and reset on every restart, giving a clean, reproducible dataset each run.
