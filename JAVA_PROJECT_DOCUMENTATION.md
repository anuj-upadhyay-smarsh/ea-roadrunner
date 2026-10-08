# EA Roadrunner (Java/Spring Boot) - Complete Project Documentation

> This document is the Java/Spring Boot counterpart of the legacy `PROJECT_DOCUMENTATION.md` (Python/FastAPI). It is an **implementation reference** for the greenfield rewrite (`ea-roadrunner-java`). Every legacy feature is mapped to its Java design so engineers can implement remaining work with feature parity.

## Table of Contents
1. [Project Overview](#project-overview)
2. [Architecture](#architecture)
3. [Module & Package Structure](#module--package-structure)
4. [Core Components](#core-components)
5. [Reconciliation Engines](#reconciliation-engines)
6. [Multi-Tenancy](#multi-tenancy)
7. [Data Access Layer](#data-access-layer)
8. [REST API](#rest-api)
9. [Infrastructure Services](#infrastructure-services)
10. [Scheduling & Resilience](#scheduling--resilience)
11. [Health Checks & Monitoring](#health-checks--monitoring)
12. [Security](#security)
13. [Configuration](#configuration)
14. [Build, Run & Deployment](#build-run--deployment)
15. [Testing](#testing)
16. [Migration Strategy](#migration-strategy)
17. [Feature Parity Matrix (Python -> Java)](#feature-parity-matrix-python---java)
18. [Implementation Roadmap](#implementation-roadmap)
19. [Appendix](#appendix)

---

## Project Overview

**EA Roadrunner (Java)** is the greenfield rewrite of the legacy Python/FastAPI reconciliation (recon) application. It is a multi-tenant, cloud-native Spring Boot service that handles reconciliation workflows for archive data across multiple customer environments (JPMC, Natwest, BNY, Deutsche Bank, RBC, and others).

The rewrite replaces the Python monolith + Celery workers with a single Spring Boot application that uses:
- An **engine registry pattern** for pluggable recon strategies (replacing the standalone Python recon scripts).
- **Spring `@Scheduled`** jobs for periodic execution (replacing Celery beat/worker).
- **Request-scoped `TenantContext`** for enforced multi-tenancy (replacing global Python config).

### Key Features
- **Multi-Tenant by Design**: `TenantContext` request-scoped bean enforces per-request tenant isolation; all repository queries are tenant-filtered.
- **Pluggable Recon Engines**: `generic`, `hybrid`, `simplified`, `state-corrector`, `item-level` implementations auto-discovered via `EngineRegistry`.
- **Automated Scheduling**: Spring `@Scheduled` cron jobs for hourly/daily/6-hourly recon runs.
- **Health Monitoring**: Spring Actuator health endpoints, Micrometer + Datadog metrics.
- **REST API + OpenAPI**: `@RestController` endpoints documented via springdoc-openapi/Swagger UI.
- **OAuth2 Resource Server**: JWT-based authentication with role-based access control.
- **Audit Logging**: AOP `@Audited` aspect for data mutation tracking with `tenant_id`/`user_id`.
- **Infrastructure Integrations**: MongoDB, Elasticsearch, Redis, AWS S3, SFTP.

### Technology Stack
- **Language**: Java 21 (LTS, virtual threads ready)
- **Framework**: Spring Boot 3.5.x, Spring Security (OAuth2 Resource Server), Spring AOP
- **Build Tool**: Maven (Spring Boot parent BOM)
- **Database**: MongoDB (Spring Data MongoDB), Elasticsearch 7.17 (REST high-level client)
- **Cache/Broker**: Redis (Spring Data Redis)
- **Cloud**: AWS S3 (spring-cloud-aws), Kubernetes
- **File Transfer**: SFTP via JSch
- **Authentication**: OAuth2/OIDC (Okta), JWT
- **Observability**: Micrometer (Datadog registry), Micrometer Tracing (OTEL bridge), Smarsh o11y/observability SDK, Log4j2
- **API Docs**: springdoc-openapi (Swagger UI)
- **Resilience**: Resilience4j (planned, version pinned in BOM)
- **Testing**: JUnit 5, Mockito, Testcontainers (MongoDB), Spring Boot Test
- **Utilities**: Lombok

---

## Architecture

### High-Level Architecture
```
┌─────────────────────────────────────────────────────────────┐
│                 Spring Boot Application                      │
│  (Single deployable JAR / container image)                   │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐ │
│  │  API Layer (@RestController)                             │ │
│  │  OAuth2 Resource Server · OpenAPI · GlobalExceptionHndlr │ │
│  └───────────────────────┬────────────────────────────────┘ │
│                          │                                   │
│  ┌───────────────────────┴────────────────────────────────┐ │
│  │  TenantContext Filter (X-Tenant-ID + JWT -> context)    │ │
│  └───────────────────────┬────────────────────────────────┘ │
│                          │                                   │
│  ┌───────────────────────┴────────────────────────────────┐ │
│  │  Service Layer (ReconService, @Async, @Audited)         │ │
│  └───────────────────────┬────────────────────────────────┘ │
│                          │                                   │
│  ┌───────────────────────┴────────────────────────────────┐ │
│  │  EngineRegistry  ->  ReconEngine implementations        │ │
│  │  generic · hybrid · simplified · state-corrector · item │ │
│  └───────────────────────┬────────────────────────────────┘ │
│                          │                                   │
│  ┌───────────────────────┴────────────────────────────────┐ │
│  │  Scheduler (@Scheduled cron jobs)                       │ │
│  └─────────────────────────────────────────────────────────┘ │
└─────────────────┬───────────────────────────────────────────┘
                  │
   ┌──────────────┼──────────────┬──────────────┬─────────────┐
   ▼              ▼              ▼              ▼             ▼
┌────────┐  ┌────────────┐  ┌────────┐  ┌──────────┐  ┌──────────┐
│MongoDB │  │Elasticsearch│ │ Redis  │  │ AWS S3   │  │  SFTP    │
└────────┘  └────────────┘  └────────┘  └──────────┘  └──────────┘
```

### Key Architecture Decisions
- **Single Spring Boot module (current state)**: The project currently builds as a single-module Maven app (`com.smarsh.roadrunner:ea-roadrunner`). The original rewrite plan (`Rewrite.md`) described a multi-module Maven layout (`common`, `api-server`, `engine-core`, `engines`, etc.); package-level separation under `com.smarsh.roadrunner` mirrors those modules today.
- **Request-Scoped TenantContext**: Per-request isolation; ThreadLocal-backed to survive `@Async` operations.
- **Engine Registry Pattern (Open/Closed)**: New recon types added as `@Component` without touching core code.
- **Data-Level Multi-Tenancy**: All MongoDB queries include `tenantId`; base `TenantRepository` enforces tenant filtering.
- **AOP Audit Logging**: `@Audited` + `AuditAspect` for non-invasive mutation tracking.

---

## Module & Package Structure

Base package: `com.smarsh.roadrunner`

```
src/main/java/com/smarsh/roadrunner/
├── api/                              # API layer (api-server module equivalent)
│   ├── Application.java              # Spring Boot entry point (@SpringBootApplication, @EnableAsync)
│   ├── config/
│   │   ├── SecurityConfig.java       # OAuth2 resource server, public/protected routes
│   │   └── TenantContextConfig.java  # Request-scoped TenantContext bean factory
│   ├── controller/
│   │   └── ReconController.java      # /api/recon endpoints
│   ├── dto/                          # Request/response DTOs (to be populated)
│   ├── exception/
│   │   └── GlobalExceptionHandler.java # @ControllerAdvice centralized errors
│   ├── filter/                       # Servlet filters (tenant extraction)
│   └── service/
│       └── ReconService.java         # Orchestration, sync/async execute, audit
├── common/                           # common module equivalent
│   ├── context/
│   │   └── TenantContext.java        # tenantId, userId, email, roles, environment
│   └── model/
│       └── ReconResult.java          # Recon result + ReconStatus enum + metrics
├── data/                             # data-access module equivalent
│   ├── entity/
│   │   └── ReconMetrics.java         # MongoDB document (tenant_id indexed)
│   └── repository/
│       ├── TenantRepository.java     # Base tenant-filtered repository
│       └── ReconMetricsRepository.java
├── engine/                           # engine-core + engines module equivalents
│   ├── api/
│   │   └── ReconEngine.java          # Engine contract interface
│   ├── registry/
│   │   └── EngineRegistry.java       # Auto-discovery factory + EngineInfo record
│   ├── generic/GenericReconEngine.java
│   ├── hybrid/HybridReconEngine.java
│   ├── simplified/SimplifiedReconEngine.java
│   ├── statecorrector/StateCorrectorEngine.java
│   └── itemlevel/ItemLevelReconEngine.java
├── infra/                            # infrastructure module equivalent
│   └── audit/
│       ├── Audited.java              # @Audited annotation
│       └── AuditAspect.java          # AOP mutation logging
└── scheduler/                        # scheduler module equivalent
    └── job/
        └── ScheduledJobs.java        # @Scheduled cron jobs
```

---

## Core Components

### Application Entry Point — `api/Application.java`
- `@SpringBootApplication` with component scanning across `com.smarsh.roadrunner.*`.
- `@EnableAsync` for non-blocking execution.
- Actuator health checks at `/actuator/health`; Swagger UI at `/swagger-ui`.
- **Legacy equivalent**: `main.py` (FastAPI app entry).

### Reconciliation Result Model — `common/model/ReconResult.java`
Tracks the outcome of every recon execution:
- Fields: `reconId`, `tenantId`, `engineType`, `status`, `processedCount`, `matchedCount`, `unreconciled`, `errors`, `startTime`, `endTime`, `durationMs`, `metadata`, `errorMessage`.
- `ReconStatus` enum: `PENDING`, `RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED`.
- `getSuccessRate()` computes `matchedCount / processedCount * 100`.
- `setEndTime()` auto-computes `durationMs`.
- **Legacy equivalent**: ad-hoc dicts and `recon_metrics_api` payloads in Python scripts.

### Service Layer — `api/service/ReconService.java`
- Orchestrates engine execution via `EngineRegistry`.
- `executeReconSync()` for synchronous runs; `executeReconAsync()` annotated with `@Async`.
- Engine health checks; mutation methods annotated with `@Audited`.
- All methods are tenant-aware (operate on `TenantContext`).
- **Legacy equivalent**: `worker.py` task dispatch logic.

---

## Reconciliation Engines

All engines implement the `ReconEngine` interface and are registered as `@Component` beans, auto-discovered by `EngineRegistry` at startup.

### Engine Contract — `engine/api/ReconEngine.java`
```java
public interface ReconEngine {
    String getEngineType();
    String getDescription();
    ReconResult execute(TenantContext tenantContext, Map<String, Object> parameters) throws Exception;
    boolean isHealthy();
}
```

### Engine Registry — `engine/registry/EngineRegistry.java`
- Auto-discovers all `ReconEngine` beans via constructor/`@Autowired` `List<ReconEngine>` injection.
- `@PostConstruct validateEngines()` logs every registered engine + health status at startup.
- Detects duplicate engine types with a warning.
- Lookups: `getEngine(type)`, `hasEngine(type)`, `getAvailableEngineTypes()`.
- Filtering: `getHealthyEngines()`, `getEngineInfoList()` (returns `EngineInfo` record list for API serialization).
- Unknown-type errors list all available types.

### Engine Catalog

| Engine Type | Class | Strategy | Default Volume | Legacy Script |
|---|---|---|---|---|
| `generic` | `GenericReconEngine` | Basic source-vs-archive comparison | 1,000 | `generic_recon.py` |
| `hybrid` | `HybridReconEngine` | 3-pass exact → fuzzy → threshold matching | 5,000 | `hybrid_recon.py` |
| `simplified` | `SimplifiedReconEngine` | Batched single-pass exact (high-volume) | 50,000 | `simplified_recon.py` |
| `state-corrector` | `StateCorrectorEngine` | Detect + correct stale record states | 10,000 | `stateCorrector.py` |
| `item-level` | `ItemLevelReconEngine` | Field-by-field item-granularity compare | 100,000 | item-level section of `generic_recon.py` |

#### 1. GenericReconEngine (`generic`)
- Base reconciliation engine; compares data sources and generates matching results.
- **Target implementation** (Phase 3+): query MongoDB `recon_metrics` (tenant-filtered) → query Elasticsearch archive metrics → query S3 source data → reconcile → generate CSV → upload to customer SFTP.
- Currently returns simulated metrics (`processed=1000`, `matched=980`).

#### 2. HybridReconEngine (`hybrid`)
- Three-pass matching: **Pass 1** exact (primary/unique key), **Pass 2** fuzzy (normalized fields, default tolerance `0.85`), **Pass 3** threshold (aggregate numeric proximity, default ±2%).
- Unmatched items cascade pass-to-pass.
- Parameters: `recordCount`, `fuzzyTolerance`, `thresholdPct`.
- Metadata: `exactMatched`, `fuzzyMatched`, `thresholdMatched`, tolerances, `engineVersion`.

#### 3. SimplifiedReconEngine (`simplified`)
- Batch-window single-pass exact matching, designed for high volume (50k–1M+).
- Null/missing fields skipped (not flagged as errors).
- Parameters: `recordCount`, `batchSize` (default 1,000), `matchRatio` (default 0.99).
- Uses Java 21 `record` (`BatchResult`) for batch stats; metadata: `batchCount`, `batchSize`, `matchRatio`.

#### 4. StateCorrectorEngine (`state-corrector`)
- Two-phase: **Phase 1 Detection** (persisted state vs event-log state divergence), **Phase 2 Correction** (safe state transitions + correction audit event per record).
- Dry-run mode via `dryRun=true` or `correctionEnabled=false`.
- Metadata: `staleDetected`, `corrected`, `correctionFailed`, `cleanRecords`, `correctionEnabled`.
- Accounting invariant: `matched + unreconciled + errors == processed`.
- **Legacy parity**: verifies `Queued`, `Fatal`, `Reprocessing` states; marks Archived/Duplicate. Default window 10 days→3 days before current date; runs daily 9 AM PST.

#### 5. ItemLevelReconEngine (`item-level`)
- Field-by-field item reconciliation; categorizes discrepancies as `MISSING`, `EXTRA`, `MISMATCH`, `MATCHED`.
- Parameters: `itemCount`, `fieldsPerItem`, `missingRatio`, `extraRatio`, `mismatchRatio`.
- Metadata: `missingItems`, `extraItems`, `mismatchItems`, `matchedItems`, `totalFieldsCompared`, `fieldMismatches`.

### Engines Pending Implementation (Parity Gaps)
The following legacy recon flows still need engine/service equivalents (planned Phase 3–4):
- **Reactive Recon** (`reactive_recon.py`) — JPMC tar pull from SFTP, untar, compare, upload.
- **Upload All Archived** (`upload_all_archived.py`) — bulk archived transcript_id upload to SFTP.
- **Internal Reactive Recon** (`internal_reactive_recon_update_db.py`) — non-JPMC/Natwest DB updates.
- **Aggregate Metrics** (`recon_metrics_aggregate.py`, `recon_metrics_aggregate_report_source.py`).
- **Feed Tracker / Ageing Report / Missed EGW Messages** (sub-features of `generic_recon.py`).

---

## Multi-Tenancy

### TenantContext — `common/context/TenantContext.java`
Immutable per-request holder of tenant identity:
- Fields: `tenantId`, `userId`, `email`, `roles` (Set), `environment`.
- Factory: `TenantContext.from(tenantId, Authentication, environment)` extracts roles from `GrantedAuthority` set.
- `hasRole(role)` checks `ROLE_` prefix; `hasAnyRole(...)` for multi-role checks.

### TenantContextConfig — `api/config/TenantContextConfig.java`
- Spring bean factory that extracts the tenant from the `X-Tenant-ID` header and the authenticated principal.
- Request-scoped; ThreadLocal-backed for `@Async` safety.
- Injected directly into controller method signatures (see `ReconController`).

### Enforcement
- Every `MongoRepository` query includes `tenantId` (see Data Access Layer).
- No cross-tenant data access: `findByIdAndTenantId(...)` instead of `findById(...)`.

---

## Data Access Layer

### Entity — `data/entity/ReconMetrics.java`
- MongoDB document with a `tenant_id` index.
- Stores per-execution recon metrics (the Java equivalent of the `recon_metrics_api` Mongo collection).

### Base Repository — `data/repository/TenantRepository.java`
```java
public interface TenantRepository<T, ID> extends MongoRepository<T, ID> {
    Optional<T> findByIdAndTenantId(ID id, String tenantId);
    List<T> findByTenantId(String tenantId);
}
```
All collection repositories should extend this to inherit tenant-safe queries.

### ReconMetricsRepository — `data/repository/ReconMetricsRepository.java`
- `findByIdAndTenantId(id, tenantId)` — prevents cross-tenant access.
- `findByTenantId(tenantId)` — tenant-scoped list.
- `findByTenantIdAndEngineType(tenantId, engineType)`.
- `@Query("{ 'tenantId': ?0, 'createdAt': { $gte: ?1 } }") findRecentForTenant(tenantId, since)`.
- `countByTenantIdAndStatus(tenantId, status)`.

### Collections to Port (from legacy `data_access_layer/`)
The legacy app has ~24 Mongo collection models. Java entities/repositories to be added for parity:
`archive_metrics`, `recon_metrics_api`, `recon_metrics_api_aggregate`, `recon_metrics_api_sinc`, `recon_metrics_cnr`, `recon_metrics_egw_missed_tracker`, `recon_metrics_file_tracker`, `recon_metrics_ic_failures`, `recon_metrics_failure_analysis`, `recon_metrics_system_health`, `recon_metrics_ui`, `recon_app_tracker`, `recon_scripts_history`, `archive_metrics_update_tracker`, `auto_remediation_ic_report`, `compliance_audit_deleted`, `egw_archived_raw_reprocess`, `endpoint`, `failed_config`, `index_metadata`, `indexing_skip_config`, `job_request_config`, `storage_archive_disposed_report`, `tenancy`.

---

## REST API

Base path: `/api` (context path) — controllers under `/api/recon`.

### ReconController — `api/controller/ReconController.java`

| Method | Endpoint | Description | Status |
|---|---|---|---|
| `GET` | `/api/recon/health` | Liveness check → `{"status":"UP"}` | Implemented |
| `POST` | `/api/recon/execute?engineType={type}` | Trigger recon for an engine (body: parameters map). Returns `202 Accepted` with `ReconResult` (currently `PENDING`) | Stub — wire to `ReconService`/`EngineRegistry` |
| `GET` | `/api/recon/status/{reconId}` | Get execution status of a recon run | Stub — wire to repository lookup |

Planned endpoints for parity / Phase 1 plan:
- `GET /api/recon/engines` — list available engines (`EngineRegistry.getEngineInfoList()`).
- Dashboard/report endpoints replacing legacy Jinja2 templates (`recon_dashboard`, `reactive_recon_results`, `cron_results`).

`TenantContext` is injected directly into controller method parameters (resolved by `TenantContextConfig`).

### OpenAPI / Swagger
- springdoc-openapi exposes interactive docs at `/swagger-ui` and the spec at `/v3/api-docs`.

### Exception Handling — `api/exception/GlobalExceptionHandler.java`
- `@ControllerAdvice` mapping `IllegalArgumentException`, `IllegalStateException`, and generic exceptions to appropriate HTTP statuses.

---

## Infrastructure Services

### Audit Logging — `infra/audit/`
- `Audited.java`: custom annotation marking methods that mutate data.
- `AuditAspect.java`: `@Aspect` that logs mutations with `tenant_id`, `user_id`, timestamp.
- **Legacy equivalent**: scattered logging + `recon_scripts_history`.

### S3 — AWS S3 (`spring-cloud-aws-starter-s3`)
- Source/result bucket operations (Cloud Capture, EGW, `EA_Archived` folders).
- **Legacy equivalent**: `common/s3_client.py` (boto3).

### Elasticsearch — `elasticsearch-rest-high-level-client` 7.17.9
- Archive metrics queries (iDoc / object store lookups).
- **Legacy equivalent**: `common/es_client.py`.

### SFTP — JSch 0.1.55
- Customer file upload/download (tar pull, CSV upload).
- **Legacy equivalent**: `common/sftp_connection.py` (paramiko) + `upload_files_to_sftp.py`.

### Redis — Spring Data Redis
- Caching layer (replaces Celery/Redis broker usage from legacy).

### Email/SMTP (planned)
- HTML + attachment mail for DHC/report notifications.
- **Legacy equivalent**: `smtp_mail_html.py`, `smtp_mail_attachment.py`.

---

## Scheduling & Resilience

### ScheduledJobs — `scheduler/job/ScheduledJobs.java`
- Spring `@Scheduled` cron jobs (replacing Celery beat). Currently hourly / daily / 6-hourly recon triggers.
- Ready for `@Async` decoration for non-blocking execution.

### Planned (Phase 3)
- Per-engine cron triggers mapped from legacy schedules:
  - Reactive/Simplified/Upload-All: daily.
  - Generic: every 4 hours.
  - StateCorrector: daily 9 AM PST.
  - DHC + failure dashboard: daily.
- **Resilience4j** retry + circuit-breaker on I/O-heavy operations.
- MongoDB-based job lock to prevent concurrent old/new system execution per tenant (migration risk mitigation).
- Job monitoring dashboard / admin UI.

---

## Health Checks & Monitoring

### Spring Actuator
- `/actuator/health` (liveness/readiness), plus standard actuator endpoints.

### Metrics — Micrometer + Datadog
- `micrometer-registry-datadog` for custom recon metrics.
- Tags should include `engine_type` and `tenant_id`.
- **Legacy equivalent**: `datadog_send_metrics.py`, `archive_metrics_failure_dashbord.py`.
- Dashboard: https://app.datadoghq.com/dashboard/abt-wki-arz/ea-archive-metrics-failure-dashboard

### Tracing & Logging
- `micrometer-tracing-bridge-otel` for distributed tracing (OTEL/Honeycomb).
- Log4j2 (`spring-boot-starter-log4j2`) for structured JSON logging with `tenant_id` tags.
- Smarsh `o11y-sdk` / `observability` SDK integration.

### Daily Health Check (DHC) — Parity Gap
Legacy `Daily_Health_Checks.py` + `dhc_archive/` modules (CSV/LDIF import, search metrics, disposition, security, ingestion stats, size metrics, failed-count analysis, archived raw counts, exports) are **not yet ported**. To be implemented as scheduled report services emitting email + Datadog.

---

## Security

### Authentication — `api/config/SecurityConfig.java`
- Spring Security **OAuth2 Resource Server** with JWT validation.
- Public endpoints: `/health`, `/swagger-ui`, `/v3/api-docs`, `/actuator/health`.
- All other endpoints require authentication.
- **Legacy equivalent**: FastAPI Okta OAuth2/OIDC flow in `main.py`.

### Authorization
- Role-based access control via `TenantContext.hasRole()` / `hasAnyRole()` (roles sourced from JWT authorities).
- Recon-team roles gate script/engine invocation.

### Secrets Management
- Externalized via environment variables / Kubernetes secrets. No hardcoded credentials.

---

## Configuration

### `application.yml` (api/config + resources)
Key configuration areas:
- **Server**: context path `/api`, port `8080`.
- **MongoDB**: `spring.data.mongodb.*` (host, port, database, credentials).
- **Redis**: `spring.data.redis.*`.
- **Elasticsearch**: host/user/password.
- **Security**: `spring.security.oauth2.resourceserver.jwt.*` (issuer/JWKS).
- **Actuator/Management**: exposed endpoints.
- **Datadog**: `management.datadog.metrics.export.*`.

### Environment Variables (parity with legacy)
| Variable | Purpose |
|---|---|
| `TENANT_NAME` | Customer/environment identifier |
| `RECON_TYPE` | Engine type key (generic/hybrid/...) |
| `IS_DEV` | Development mode flag |
| `OIDC_BASE_URL` / `OIDC_CLIENT_ID` / `OIDC_CLIENT_SECRET` / `OIDC_REDIRECT_URL` | OAuth config |
| `MONGO_HOST` / `MONGO_PORT` / `MONGO_DB` | MongoDB |
| `ES_ARCHIVE_HOST` / `ES_ARCHIVE_HOST_USER` / `ES_ARCHIVE_HOST_PASSWD` | Elasticsearch |
| `DATADOG_API_KEY` | Datadog metrics |
| `STATECORRECTOR_STATES` | `Queued,Fatal,Reprocessing,Stored,Failed_Parked,Failed` |
| `DHC_MAIL_HOUR` | DHC report hour (default 9) |
| `THREAD_POOL_SIZE` / `CHUNK_SIZE` / `AM_CHUNK_SIZE` | Performance tuning |
| `SFTP_HEALTH_CHECK_ENABLED` | SFTP health check toggle |
| `X-Tenant-ID` (header) | Per-request tenant identifier |

### Maven Coordinates
- `groupId`: `com.smarsh.roadrunner`, `artifactId`: `ea-roadrunner`, `version`: `2.0.0-SNAPSHOT`.
- Parent: `spring-boot-starter-parent` 3.5.x; Java 21.
- Smarsh internal repos: `https://smarsh.jfrog.io/smarsh/libs-release`.

---

## Build, Run & Deployment

### Prerequisites
- Java 21
- Maven 3.8+
- Docker & Docker Compose

### Local Development
```bash
# 1. Start infrastructure (MongoDB 7, Redis 7, Elasticsearch 8.10)
docker-compose up -d

# 2. Build
mvn clean install

# 3. Run API server
mvn spring-boot:run

# 4. Access
open http://localhost:8080/api/swagger-ui.html
curl http://localhost:8080/api/actuator/health
```

### Docker Compose (`docker-compose.yml`)
- `mongodb` (7.0, admin/password, healthcheck, persistent volume)
- `redis` (7, healthcheck)
- `elasticsearch` (8.10, single-node, healthcheck)
- Shared network for inter-service communication.

### Kubernetes Deployment (parity with legacy `cd/k8s/`)
- Base `deployment.yaml`, `service.yaml`, `serviceaccount.yaml`, `kustomization.yaml`.
- Overlays per cloud/region/customer (JPMC NAM/EMEA/APAC/Swiss, Natwest Prod/UAT, BNY, Deutsche Bank, RBC, DTCC, Schwab, Putnam, Principal, Hartford, SMBC, Santander POC).
- Container image built from a Spring Boot Dockerfile (JAR or GraalVM native image).

---

## Testing

### Frameworks
- **JUnit 5 + Mockito** for unit tests.
- **Testcontainers (MongoDB 7.0)** for integration tests.
- **Spring Boot Test** for context/web-layer tests.

### Existing Tests
| Test Class | Scope | Cases |
|---|---|---|
| `TenantContextTest` | Tenant context behavior | 4 |
| `GenericReconEngineTest` | generic engine | 4 |
| `HybridReconEngineTest` | hybrid engine | 6 |
| `SimplifiedReconEngineTest` | simplified engine | 6 |
| `StateCorrectorEngineTest` | state-corrector engine | 6 |
| `ItemLevelReconEngineTest` | item-level engine | 7 |
| `EngineIntegrationTest` | all 5 engines, real MongoDB, multi-tenant isolation | 16 |

Total: 33 unit tests + 16 integration tests.

### Commands
```bash
mvn test                                       # all unit tests
mvn test -Dtest="*EngineTest,EngineIntegrationTest"  # engine tests
mvn verify                                     # full build incl. integration tests (Docker required)
```

### Coverage Goal
- Target ≥ 85% (unit + integration), per the rewrite success criteria.

---

## Migration Strategy

Phased parallel migration (from `Rewrite.md`):
- **Phase 0 – Foundation** ✅: Maven project, Spring Boot 3.x, TenantContext, security, base repos, engine abstraction, Docker Compose.
- **Phase 1 – Core API & Services** ✅: REST controllers, `GenericReconEngine`, `ReconService`, repositories, exception handling, unit tests.
- **Phase 2 – Remaining Engines** ✅: Hybrid, Simplified, StateCorrector, ItemLevel engines + upgraded `EngineRegistry`; unit + integration tests.
- **Phase 3 – Scheduling & Resilience** 🔜: `@Scheduled` cron, `@Async`, Resilience4j, Datadog dashboards, admin UI.
- **Phase 4 – Data Migration & Validation** 🔜: `LegacyDataSyncService`, `DataIntegrityValidator`, shadow-write, parity validation on 1M+ records.
- **Phase 5 – Production Cutover** 🔜: canary (1 customer), cohort migration (5–10/week), legacy decommission.

### Cutover Safeguards
- MongoDB job lock so only one system runs per tenant.
- 100% field-by-field data parity required before cutover.
- DNS-flip rollback within 5 minutes; parallel infra maintained 2 weeks.

---

## Feature Parity Matrix (Python -> Java)

| Legacy (Python/FastAPI) | Java/Spring Boot Equivalent | Status |
|---|---|---|
| `main.py` (FastAPI app) | `api/Application.java` + controllers | ✅ Core done |
| `worker.py` (Celery tasks) | `scheduler/job/ScheduledJobs.java` + `ReconService` | 🔜 partial |
| `generic_recon.py` | `engine/generic/GenericReconEngine.java` | ✅ stub / 🔜 full I/O |
| `hybrid_recon.py` | `engine/hybrid/HybridReconEngine.java` | ✅ |
| `simplified_recon.py` | `engine/simplified/SimplifiedReconEngine.java` | ✅ |
| `stateCorrector.py` | `engine/statecorrector/StateCorrectorEngine.java` | ✅ |
| item-level recon (in generic) | `engine/itemlevel/ItemLevelReconEngine.java` | ✅ |
| `reactive_recon.py` | ReactiveReconEngine (planned) | 🔜 |
| `upload_all_archived.py` | UploadAllArchived service (planned) | 🔜 |
| `internal_reactive_recon_update_db.py` | InternalReactiveRecon service (planned) | 🔜 |
| `recon_metrics_aggregate*.py` | Aggregate report services (planned) | 🔜 |
| `Daily_Health_Checks.py` + `dhc_archive/` | DHC report services (planned) | 🔜 |
| `archive_metrics_failure_dashbord.py` | Micrometer/Datadog metrics publisher | 🔜 |
| `invoke_*_api.py` (JIRA, IC, index gateway, archive raw, remediation, ingestion) | API client services (planned) | 🔜 |
| `generate_dlp_reports.py`, `csurv_report.py` | Report services (planned) | 🔜 |
| `common/s3_client.py` | spring-cloud-aws S3 | 🔜 wiring |
| `common/es_client.py` | ES REST high-level client | 🔜 wiring |
| `common/sftp_connection.py` | JSch SFTP service | 🔜 wiring |
| `common/mongo_connection.py` | Spring Data MongoDB | ✅ |
| Okta OAuth2 in `main.py` | `SecurityConfig` OAuth2 resource server | ✅ |
| Jinja2 templates / Bootstrap UI | OpenAPI + (planned) admin UI | 🔜 |
| `data_access_layer/*` (24 collections) | `data/entity` + `data/repository` | ✅ 1 done / 🔜 rest |
| pytest suite (95%+) | JUnit 5 + Testcontainers (target 85%+) | ✅ partial |

---

## Implementation Roadmap

Recommended next implementation steps for engineers:
- **Wire `ReconController.execute` to `ReconService`/`EngineRegistry`** so `POST /api/recon/execute` runs a real engine and persists a `ReconMetrics` document.
- **Implement `GET /api/recon/engines`** using `EngineRegistry.getEngineInfoList()`.
- **Implement real I/O in `GenericReconEngine`**: MongoDB (tenant-filtered) → Elasticsearch archive metrics → S3 source → reconcile → CSV → SFTP upload.
- **Add remaining Mongo entities/repositories** extending `TenantRepository` for the 24 legacy collections.
- **Port reactive/upload-all/internal-reactive/aggregate flows** as new engines or services.
- **Build DHC + failure-dashboard scheduled services** (email + Datadog).
- **Phase 3**: Resilience4j, per-engine cron schedules, job lock, admin UI.

---

## Appendix

### Glossary
- **EA**: Enterprise Archive
- **DHC**: Daily Health Check
- **SFTP**: Secure File Transfer Protocol
- **CNR**: Could Not Receive
- **EGW**: Email Gateway
- **iDoc**: Internal Document
- **SINC**: System Incident
- **DLP**: Data Loss Prevention
- **CSURV**: Customer Surveillance
- **IC**: Integrity Check
- **AM**: Archive Metrics
- **RLS**: Row-Level Security (tenant-scoped access)

### Customer Acronyms
- **JPMC**: JPMorgan Chase · **BNY**: Bank of New York Mellon · **DTCC**: Depository Trust & Clearing Corporation · **RBC**: Royal Bank of Canada · **SMBC**: Sumitomo Mitsui Banking Corporation

### References
- Legacy documentation: `PROJECT_DOCUMENTATION.md`
- Rewrite plan: `Rewrite.md`
- Phase reports: `PHASE_0_COMPLETION.md`, `PHASE_1_PROGRESS.md`, `PHASE_2_COMPLETION.md`
- Quick start: `README.md`
- Miro design board: https://miro.com/app/board/o9J_leH6MOM=/
- Datadog dashboard: https://app.datadoghq.com/dashboard/abt-wki-arz/ea-archive-metrics-failure-dashboard

---

**Document Version**: 1.0
**Last Updated**: 2026-06-10
**Maintained By**: Archive Insights Team
