# Bank  Platform (Spring Boot microservices)

Attendance, incentive and branch management for co-operative and commercial banks, rebuilt as a
Spring Boot 3 / Spring Cloud microservice backend (Java 17).

## Architecture

```
                   ┌──────────────┐
  client ───────►  │ api-gateway  │ :8080  JWT check, routing, CORS, auth rate limit, Swagger
                   └──────┬───────┘
          ┌───────────────┼────────────────┬─────────────────┐
   ┌──────▼──────┐ ┌──────▼────────────┐ ┌─▼────────────────┐ ┌▼─────────────┐
   │ auth-service│ │organization-service│ │attendance-service│ │ audit-service│
   │   :8081     │ │      :8082         │ │     :8083        │ │    :8084     │
   └──────┬──────┘ └──────┬─────────────┘ └──┬───────────────┘ └──────▲───────┘
          │ schema auth   │ schema organization│ schema attendance     │ schema audit
          └───────── audit events ──► RabbitMQ (bank.audit) ─────┘
   incentive-service :8085 (schema incentive) · discovery-server (Eureka) :8761
```

### Database layout

One PostgreSQL database, `bank`, with **one schema per service**:

```
bank
├── auth          users, password_history, refresh_tokens, role_permissions, tenant_access
├── organization  tenants, banks, branches, employees, designations, custom_roles, announcements
├── attendance    attendance_records, attendance_rules, regularizations, leave_policies,
│                 leave_requests, holidays, timesheets
├── incentive     products, incentive_plans, customers
└── audit         audit_logs, audit_chain_heads
```

Each service is pinned to its schema (`spring.datasource.hikari.schema`,
`hibernate.default_schema`, `spring.flyway.schemas`); its Flyway history table lives in the same
schema, so services migrate independently. Services never join across schemas — data from another
service is fetched through its API.

Isolation is enforced by PostgreSQL, not just by convention: [01-init-schemas.sh](docker/postgres/01-init-schemas.sh)
creates one login per service (`auth_svc`, `organization_svc`, `attendance_svc`, `incentive_svc`,
`audit_svc`), each **owning only its own schema**, and revokes default rights on the database and
`public`. A service that tries to read another schema gets `permission denied`. The superuser
(`DB_ADMIN_USERNAME`) is used only by the container to run this script.

| Module | Responsibility |
|---|---|
| `common-lib` | Shared security (JWT → user, data scope), RFC 7807 errors, `ApiResponse`, JPA auditing, audit publisher, Feign token forwarding, OpenAPI, CSV export |
| `discovery-server` | Eureka registry (HTTP Basic protected) |
| `api-gateway` | Single entry point; verifies JWTs, strips spoofable forwarding headers, per-IP rate limit on `/api/v1/auth/**`, aggregated Swagger UI |
| `auth-service` | Sign-in, RS256 access tokens + rotating refresh tokens, lockout, password policy/history, permission matrix, impersonation, JWKS |
| `organization-service` | Organizations (tenants) and quotas, banks, branches + geofences, employees (encrypted salary account), designations, custom roles, announcements |
| `incentive-service` (:8085) | Products, monthly incentive plans, customer leads with maker-checker conversion, leaderboard, incentive statement CSV |
| `attendance-service` | Geofenced clock-in/out, corrections, leave (balances, approvals), policies, holidays, timesheets |
| `audit-service` | Append-only, SHA-256 hash-chained audit trail per tenant with verification and CSV export |

## Security model

- **Authentication**: BCrypt (cost 12) passwords; RS256 JWT access tokens (15 min) signed only by
  auth-service; other services verify via JWKS (issuer + audience validated). Refresh tokens are
  opaque, stored as SHA-256 hashes, single-use with rotation; replaying a used token revokes the
  whole sign-in family.
- **Brute force**: per-account lockout with exponential back-off (15 min → 8 h), constant-time
  response for unknown usernames, plus gateway per-IP throttling of auth endpoints.
- **Passwords**: min 10 chars, mixed case, digit, symbol; no username/name/common words; last 3
  cannot be reused; admin-issued passwords are one-time and must be changed at first sign-in.
- **Authorization**: role × module CRUD matrix embedded in the token (`leaves:U`), enforced with
  `@PreAuthorize` and again in services. Data scope: SA/HR → organization, BM → own branch,
  others → self. Approvals: never your own request, only a strictly junior subject.
- **Multi-tenancy**: every table carries `tenant_code`; every query filters by the tenant from the
  verified token. Only the platform owner may target another tenant (`X-Tenant-Code`).
- **Service-to-service**: calls forward the caller's own token, so downstream checks still apply.
- **Data protection**: salary account numbers AES-256-GCM encrypted at rest and masked in
  responses; strict input validation (no `<`/`>` in free text); CSV exports neutralise formula
  injection; no stack traces or SQL in error responses.
- **Audit**: every write and sign-in event is published after commit to RabbitMQ and appended to a
  hash chain; a DB trigger blocks UPDATE/DELETE; `/api/v1/audit-logs/verify` detects tampering.
- **No direct access**: the gateway strips client-supplied trust headers and stamps a secret
  `X-Internal-Token` on every request; each service rejects any call without it (even with a valid
  user token). Service-to-service Feign calls carry it too. Only container health probes and the
  internal JWKS document are exempt.
- **No open API surface**: unauthenticated endpoints are only login, refresh and logout. Swagger is
  disabled by default (`SWAGGER_ENABLED=false`) and the signing-key endpoint is not routed by the
  gateway.
- **Containers**: non-root, read-only filesystem, `no-new-privileges`; only the gateway is exposed.

## Run with Docker

```bash
cp .env.example .env
# fill in: DB/RabbitMQ/Eureka passwords, DATA_ENCRYPTION_KEY (openssl rand -base64 32),
#          BOOTSTRAP_ADMIN_PASSWORD, and JWT_PRIVATE_KEY for anything beyond local testing
docker compose up -d --build
```

- Swagger UI (all services): http://localhost:8080/swagger-ui.html
- Eureka: http://localhost:8761 · RabbitMQ UI: http://localhost:15672 (local only)

### First steps

1. `POST /api/v1/auth/login` with `{"tenantCode":"platform","username":"masteradmin","password":"..."}`.
2. `POST /api/v1/auth/change-password` (required on first sign-in), use the returned token.
3. `POST /api/v1/platform/tenants` to provision an organization: creates its bank, headquarters
   branch and Super Admin, and returns the admin's one-time password.
4. Sign in as that admin (`tenantCode` = the organization code) and add branches, employees, etc.

## Build locally

```bash
mvn clean install          # compiles all modules and runs unit tests
```

Each service also runs standalone (`mvn -pl auth-service spring-boot:run`) against a local
PostgreSQL, RabbitMQ and Eureka; defaults are in each `application.yml`.

## Conventions

- Package layout per service: `config`, `domain`, `repository`, `dto`, `service`, `web`, `client`.
- Success responses: `ApiResponse { success, message, data, timestamp }`; errors: RFC 7807
  `application/problem+json` with stable `code` and `requestId`.
- Schema changes only via Flyway (`db/migration`); Hibernate runs in `validate` mode.
- Every request carries `X-Request-Id`, logged in MDC and propagated across services.

## Incentive rules

- Every customer record starts as a **LEAD**. Only a senior in scope who is neither the owner nor
  the maker can mark it **CONVERTED** (also enforced by a DB constraint `checker_id <> maker_id`).
- A plan's `target` is the **per-employee monthly target** for its category (LOAN, DEPOSIT or ANY).
  Below `thresholdPercent` nothing is paid; above it `baseRatePercent` applies to all converted
  volume and `bonusRatePercent` to volume beyond 100% of target. Past months' plans are frozen.
- The leaderboard is visible to everyone, but incentive amounts are shown only for employees in
  the viewer's scope (self / branch / organization).

## Platform owner (SSA) endpoints

| Endpoint | Purpose |
|---|---|
| `GET/POST /api/v1/platform/tenants`, `PUT /tenants/{code}` | provision organizations, plan, quotas, features, suspend/activate |
| `GET /api/v1/platform/stats` | organizations, banks, branches, active staff |
| `GET /api/v1/platform/banks`, `/branches` | allocations across organizations |
| `GET /api/v1/platform/customers`, `/customers/overview` | customer business across organizations (mobiles masked) |
| `POST /api/v1/platform/tenants/{code}/reset-admin-password` | new one-time password for an organization admin |
| `POST /api/v1/platform/tenants/{code}/support-session` | audited 30-min session as the org admin (no refresh, cannot change passwords or mark attendance) |

## Dashboard

Each service owns its slice (no cross-service joins); the frontend loads all three in parallel:

| Endpoint | Content |
|---|---|
| `GET /api/v1/platform/dashboard` | active branches, active staff in scope, my branch + geofence radius, latest announcements |
| `GET /api/v1/attendance/dashboard` | today's counts by status in scope, my punch, leaves/corrections/timesheets awaiting me |
| `GET /api/v1/leaderboard/dashboard` | this month's plan, my rank/achievement/incentive, leads awaiting my approval |

"Not clocked in yet" = `activeStaffInScope - markedToday`.

## Transfers and promotions

`PATCH /api/v1/employees/{id}/assignment` changes branch, role and designation, updates the login in
auth-service and revokes the employee's sessions so the next token carries the new claims. Historic
attendance, leave and customer records keep the branch the employee had at the time.

## Roadmap

- Transactional outbox for audit events, Redis-backed distributed rate limiting,
  resilience4j circuit breakers, Testcontainers integration tests, per-service DB users,
  Kubernetes manifests and centralized config/secrets (Vault).
