# FraudShield Backend

A self-contained Spring Boot 3.2 monolith for the FraudShield demo. It runs with a single
command on an **H2 in-memory database** — no Kafka, Redis, or PostgreSQL required.

## Prerequisites

- Java 17 (JDK)
- Maven 3.9+

## Run

From this directory (`fraudshield/backend`):

```bash
mvn spring-boot:run
```

The server starts on **http://localhost:8080**. Data is seeded on startup into an in-memory
H2 database, so every restart gives a fresh, reproducible dataset.

- Swagger UI: http://localhost:8080/swagger-ui.html (click **Authorize** to paste a Bearer token)
- H2 console: http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:fraudshield`, user `sa`, empty password)
- Health: http://localhost:8080/actuator/health

CORS is enabled for the React UI origin **http://localhost:5173**. Allowed request headers are
`Authorization`, `Content-Type`, and `X-Mock-Response`; `Authorization` and `X-Mock-Response`
are exposed to the browser.

## Mock mode

The backend is the single source of data for the UI: it serves either **seeded demo data**
(mock) or only **runtime-created data** (real). Which one a list endpoint returns is controlled
by two inputs, in order of precedence:

1. The per-request header **`X-Mock-Response: true|false`** (parsed case-insensitively). When
   present and non-blank it wins for that request.
2. The config key **`fraudshield.mock.enabled`** in `application.yml` (default **`true`**),
   used when the header is absent or blank.

Seeded rows (the 48 demo transactions and 6 demo audit logs inserted on boot) carry an internal
`seeded=true` flag; anything created at runtime is `seeded=false`. In **mock** mode the list
endpoints return everything; in **real** mode they return only `seeded=false` rows. Rules are
**not** mode-filtered and rule writes persist in both modes.

Affected list endpoints: `GET /api/transactions`, `GET /api/admin/transactions`, and
`GET /api/admin/audit-logs`.

### `GET /api/config/mock-status` (public)

Returns the resolved mode for the current request:

```json
{ "mockEnabled": true, "source": "config" }
```

`source` is `"header"` when `X-Mock-Response` drove the result, `"config"` otherwise.

Example — fetch seeded transactions regardless of the config default:

```bash
curl -H "X-Mock-Response: true" http://localhost:8080/api/transactions
```

## Demo accounts (seeded)

| Email | Password | Name | Role |
|-------|----------|------|------|
| HKadmin@fraudshield.io | Admin@123 | HK Admin | ROLE_ADMIN |
| HKanalyst@fraudshield.io | Analyst@123 | HK Analyst | ROLE_ANALYST |
| HKsuperadmin@fraudshield.io | SuperAdmin@123 | HK SuperAdmin | ROLE_SUPER_ADMIN |

Passwords are stored as BCrypt hashes (strength 12).

## Fraud rules engine

Seeded rules (only **enabled** rules contribute to the score):

| id | name | weight | enabled |
|----|------|--------|---------|
| velocity | Velocity Check | 40 | true |
| amount | Amount Anomaly | 30 | true |
| geo | Geo-Mismatch | 20 | true |
| duplicate | Duplicate Detection | 10 | true |
| nightowl | Odd-Hour Spending | 15 | false |

A transaction is scored by summing the weights of fired enabled rules. `score >= 50` →
verdict `FLAGGED`, status `PENDING`; otherwise `PASSED`. Firing is deterministic for the demo
(`amount` fires when amount > 2000; `nightowl` fires for amounts > 1000 between 02:00–05:00 UTC;
`velocity`/`geo`/`duplicate` fire via a stable hash of the transaction id + merchant).

## Authentication

Stateless JWT. `POST /api/auth/login` returns an access token (15 min TTL) and a single-use
refresh token (7 day TTL). Send the access token as `Authorization: Bearer <token>`.
`POST /api/auth/refresh` rotates the refresh token (the old one is marked used).

## REST API

All paths are prefixed with `/api`.

### Auth — `/api/auth`

| Method | Path | Auth | Body | Response |
|--------|------|------|------|----------|
| POST | `/login` | public | `{email,password}` | `{accessToken,refreshToken,expiresIn:900,user:{name,email,role}}`; 401 on bad creds |
| POST | `/refresh` | public | `{refreshToken}` | new token pair; 401 if invalid/used/expired |
| POST | `/logout` | Bearer | — | 204 (marks the user's refresh tokens used) |
| GET | `/me` | Bearer | — | `{name,email,role}` |

### Transactions — `/api/transactions`

| Method | Path | Auth | Notes |
|--------|------|------|-------|
| GET | `/` | authenticated | List all, newest first. Optional `q`, `verdict`, `status` query params |
| GET | `/{id}` | authenticated | Single transaction with `triggered` rules |
| POST | `/` | authenticated | `{amount,currency,merchantId,latitude?,longitude?}`; runs the engine; **202** with `{transactionId,status,createdAt}` |

### Admin — `/api/admin` (ROLE_ANALYST and above)

| Method | Path | Auth | Notes |
|--------|------|------|-------|
| GET | `/transactions` | ANALYST+ | Analyst transaction list |
| PUT | `/verdicts/{id}` | ANALYST+ | `{decision:"FALSE_POSITIVE"\|"CONFIRMED_FRAUD"}`; `FALSE_POSITIVE` → status `OVERRIDDEN`, `CONFIRMED_FRAUD` → stays `FLAGGED`, sets confirmed flag |
| GET | `/audit-logs` | ADMIN+ | Audit entries, newest first |

### Rules — `/api/admin/rules`

| Method | Path | Auth | Notes |
|--------|------|------|-------|
| GET | `/` | ANALYST+ (read) | List rules |
| PUT | `/{id}` | ADMIN+ only | `{enabled,weight?}`; analyst receives 403 |

## Errors

Structured JSON (no stack traces): `{timestamp,status,error,message,path}`. Validation
failures return 400 with an added `fieldErrors` map. 401 for missing/invalid auth, 403 for
insufficient role.

## UI integration note

The React UI (`../UI`) currently runs on mock data. The backend's JSON field names are aligned
for a drop-in swap: transactions expose `triggered` (array of `{id,name,weight}`) and `createdAt`
as **epoch milliseconds**; rules expose both `desc` and `description`; audit logs expose `ico`
and `at` (epoch millis). Roles are the full `ROLE_*` strings the UI stores verbatim.
