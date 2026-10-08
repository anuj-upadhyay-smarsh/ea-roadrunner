# EA Roadrunner - Java/Spring Boot Rewrite

Greenfield rewrite of EA Roadrunner reconciliation platform in Java 21 + Spring Boot 3.2

## Quick Start

### Prerequisites
- Java 21
- Maven 3.8+
- Docker & Docker Compose

### Local Development Setup

1. Start infrastructure services:
```bash
docker-compose up -d
```

2. Build project:
```bash
mvn clean install
```

3. Run API server:
```bash
mvn spring-boot:run -pl api-server
```

Server runs at: http://localhost:8080/api

### Project Structure

```
ea-roadrunner-java/
├── common/              # Shared models, TenantContext, utilities
├── api-server/          # REST API, Spring Boot main app
├── engine-core/         # ReconEngine interface & registry
├── engines/             # Individual engine implementations
├── data-access/         # MongoDB repositories & entities
├── infrastructure/      # S3, Elasticsearch, audit, logging
├── scheduler/           # Cron jobs & async tasks
├── migration/           # Data sync & cutover utilities
└── docker-compose.yml   # Local development stack
```

### Architecture Highlights

- **Multi-Tenant by Design**: TenantContext @RequestScope bean ensures per-request isolation
- **Engine Registry Pattern**: Add new recon types without modifying core code
- **Data-Level Isolation**: MongoDB collections indexed by tenant_id
- **Cloud-Native**: Micrometer metrics, distributed tracing, native image ready
- **Fully Async**: Spring @Async, Project Reactor for non-blocking operations

### Phase Progress

| Phase | Status | Details |
|---|---|---|
| Phase 0 – Foundation | ✅ Complete | See `PHASE_0_COMPLETION.md` |
| Phase 1 – Core API & Services | ✅ Complete | See `PHASE_1_PROGRESS.md` |
| Phase 2 – Remaining Engines | ✅ Complete | See `PHASE_2_COMPLETION.md` |
| Phase 3 – Scheduling & Resilience | 🔜 Next | |
| Phase 4 – Data Migration & Validation | 🔜 Planned | |
| Phase 5 – Production Cutover | 🔜 Planned | |

### Recon Engines (Phase 2 Complete)

| Engine | Type Key | Strategy |
|---|---|---|
| `GenericReconEngine` | `generic` | Basic source-vs-archive comparison |
| `HybridReconEngine` | `hybrid` | 3-pass exact → fuzzy → threshold matching |
| `SimplifiedReconEngine` | `simplified` | Batched single-pass exact match (high-volume) |
| `StateCorrectorEngine` | `state-corrector` | Detect + correct stale record states |
| `ItemLevelReconEngine` | `item-level` | Field-by-field item-granularity comparison |

### Running Tests

```bash
# All unit tests
mvn test

# Engine unit + integration tests only
mvn test -Dtest="*EngineTest,EngineIntegrationTest"

# Full build with integration tests (requires Docker)
mvn verify
```

### Next Steps (Phase 3)

1. Spring `@Scheduled` cron jobs with per-engine triggers
2. `@EnableAsync` non-blocking execution
3. Resilience4j retry + circuit-breaker patterns
4. Datadog metrics dashboards for engine execution
5. Admin UI for job management

### References

- [Architecture Diagram](../docs/EA_Roadrunner_Java_Architecture.svg)
- [Greenfield Rewrite Plan](../docs/EA_Roadrunner_Greenfield_Rewrite.docx)
- [Spring Boot 3.2 Docs](https://spring.io/projects/spring-boot)
- [Spring Data MongoDB](https://spring.io/projects/spring-data-mongodb)
