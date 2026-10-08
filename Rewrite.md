EA Roadrunner Complete Redesign
Greenfield Rewrite with Parallel Migration
Executive Summary
This document outlines a complete greenfield rewrite of the EA Roadrunner App in Java/Spring Boot, replacing the legacy Python/FastAPI monolith. The new architecture will be built from scratch with modern cloud-native patterns, enforced multi-tenancy, and microservices capabilities.
Strategy
•	Rather than incrementally refactoring the existing codebase, we will:
•	Build a new repository (ea-roadrunner-java) in parallel with the legacy system
•	Implement feature parity gradually, testing each component against production data
•	Run both systems simultaneously during transition phase
•	Migrate one customer at a time to the new platform
•	Maintain full rollback capability throughout the migration
 
1. New Repository Structure
ea-roadrunner-java/ (new root)
•	  ├── pom.xml (parent BOM)
•	  ├── common/ (shared code, models, config)
•	  ├── api-server/ (REST API, security, controllers)
•	  ├── engine-core/ (ReconEngine interface, registry, execution)
•	  ├── engines/ (individual engine implementations)
•	      ├── generic-engine/
•	      ├── hybrid-engine/
•	      ├── simplified-engine/
•	      ├── state-corrector-engine/
•	      └── item-level-engine/
•	  ├── data-access/ (Spring Data, MongoDB, JPA)
•	  ├── infrastructure/ (S3, Elasticsearch, SFTP, logging, audit)
•	  ├── scheduler/ (cron jobs, scheduled tasks)
•	  ├── migration/ (legacy sync, data validation, cutover)
•	  └── docker-compose.yml (local development environment)
 
2. Development Phases (Greenfield Approach)
Phase 0: Foundation & Infrastructure
•	Duration: 3 weeks | Team: 2-3 engineers
•	Create new Maven multi-module project structure
•	Set up Spring Boot 3.2 with Spring Security OAuth 2.0
•	Implement TenantContext @RequestScope bean + filter
•	Create MongoDB Spring Data repositories with tenant filtering
•	Set up Redis caching + Datadog Micrometer integration
•	Configure Docker Compose for local development
•	Write infrastructure tests with TestContainers
•	Establish CI/CD pipeline for new repository

Phase 1: Core API & Services
•	Duration: 5 weeks | Team: 3 engineers
•	Implement REST controllers with @RestController
•	Set up OpenAPI/Swagger documentation
•	Create ReconEngine interface and registry pattern
•	Build GenericReconEngine with dependency injection
•	Implement MongoRepository with auto-tenant filtering
•	Create S3Service and ElasticsearchClient wrappers
•	Set up SLF4J JSON logging with tenant_id tags
•	Implement @Aspect AuditLogger for data mutations
•	Write unit tests with JUnit 5 + Mockito (>80% coverage)

Phase 2: Remaining Engines
•	Duration: 6 weeks | Team: 3 engineers
•	Implement HybridReconEngine with full feature parity
•	Implement SimplifiedReconEngine
•	Implement StateCorrector engine
•	Implement ItemLevelRecon engine
•	Create EngineRegistry factory with auto-discovery
•	Integration tests for each engine using TestContainers
•	Load test against staging MongoDB with 1M+ records

Phase 3: Task Scheduling & Resilience
•	Duration: 5 weeks | Team: 2 engineers
•	Create Spring @Scheduled jobs with cron expressions
•	Implement @EnableAsync for non-blocking execution
•	Build job monitoring dashboard
•	Implement Datadog metrics collection
•	Set up Spring Cloud Sleuth for distributed tracing
•	Implement retry/circuit-breaker patterns (Resilience4j)
•	Test concurrent job execution under high load
•	Create admin UI for job management

Phase 4: Data Migration & Validation
•	Duration: 6 weeks | Team: 2-3 engineers
•	Build LegacyDataSyncService (read from old MongoDB)
•	Create DataIntegrityValidator (compare outputs)
•	Implement shadow-write pattern (write to both systems)
•	Run parallel execution: old vs new on sample customers
•	Validate 100% data parity on reconciliation results
•	Create comprehensive rollback procedures
•	Automate cutover testing on staging environment
•	Document migration playbooks for ops team

Phase 5: Production Migration & Cutover
•	Duration: 5 weeks | Team: 3 engineers
•	Deploy to production in canary mode (1 customer)
•	Monitor metrics, logs, error rates for 1 week
•	Execute cohort migrations (5-10 customers per week)
•	Continuous monitoring of legacy system in parallel
•	Execute fallback procedures if needed
•	Decommission legacy Python infrastructure
•	Document operational runbooks and handoff to ops

 
3. Key Architecture Decisions
3.1 Language: Java + Spring Boot 3.2
•	Rationale:
•	Enterprise-grade stability and maturity
•	Spring ecosystem has built-in multi-tenancy support
•	Superior performance: GraalVM native images (50-100x faster startup)
•	Type safety reduces runtime errors vs. Python
•	Dependency injection prevents global configuration antipatterns
3.2 Multi-Module Maven Structure
•	Allows independent deployment and scaling of engine microservices
•	Shared code in common/ module prevents duplication
•	Each engine can have its own resource limits and scaling policies
3.3 Data-Level Multi-Tenancy
•	MongoDB role-based access control (RLS) scoped to each tenant
•	Application layer enforces tenant_id in all queries via Spring Data
•	Defense-in-depth: No accidental cross-tenant data queries
3.4 Request-Scoped TenantContext
•	@RequestScope bean injected into every service and controller
•	ThreadLocal-backed; survives Spring @Async operations
3.5 Engine Registry Pattern
•	New recon types added as @Component without modifying core code
•	Registry auto-discovers implementations at startup
 
4. Migration Strategy
4.1 Phase 1-3: Build & Test New System
•	New system built in parallel; legacy system continues processing all traffic
•	No production impact during development
4.2 Phase 4: Validation & Shadow Testing
•	LegacyDataSyncService syncs data from old to new MongoDB
•	New system executes all jobs in shadow mode; results compared to legacy
•	100% data parity validated before any customer migration
4.3 Phase 5a: Canary Deployment
•	Deploy to prod for 1 non-critical customer
•	Route their traffic to new system; monitor for 1 week
•	Rollback within 5 minutes if issues detected
4.3b: Cohort Migration
•	Weekly: Migrate next batch of 5-10 customers
•	DataIntegrityValidator runs before each cohort cutover
•	Monitor both systems for 1 week after each migration
4.4 Phase 5c: Decommission Legacy
•	After all customers migrated + 2 weeks of stability
•	Stop legacy Python app and shut down old infrastructure
•	Archive old MongoDB for 6 months (compliance)
 
5. Risk Mitigation
Risk 1: Performance Regression
•	Issue: Java/Spring Boot slower than Python for some workloads
•	Mitigation: Benchmark early (Phase 1); compare against legacy on identical data
•	Use async/reactive patterns for I/O-heavy operations
Risk 2: Data Parity Mismatch
•	Issue: New system outputs different results than legacy for same input
•	Mitigation: Run parallel validation (Phase 4); compare field-by-field
•	Require 100% parity before production cutover
Risk 3: Scheduling Conflicts
•	Issue: Both old and new systems run jobs simultaneously
•	Mitigation: Implement job lock in MongoDB; only one system can run per tenant
Risk 4: Customer Complaints Post-Cutover
•	Issue: Discover issues after production migration
•	Mitigation: Canary + 1-week observation with real traffic before general migration
Risk 5: Rollback Complexity
•	Issue: Difficult to revert if new system breaks
•	Mitigation: Maintain parallel infra for 2 weeks; DNS flip only (no DB sync)
 
6. Project Timeline & Effort Estimate
Total Duration: 30 weeks (~7 months)
Average Team Size: 2-3 engineers
Total Engineer-Days: ~700-900 (3 eng × 30 weeks)
Timeline: Jan 2026 → Sep 2026 (if starting immediately)

•	Phase Breakdown:
•	Phase 0: 3 weeks × 2.5 eng = ~37 days
•	Phase 1: 5 weeks × 3 eng = ~75 days
•	Phase 2: 6 weeks × 3 eng = ~90 days
•	Phase 3: 5 weeks × 2 eng = ~50 days
•	Phase 4: 6 weeks × 2.5 eng = ~75 days
•	Phase 5: 5 weeks × 3 eng = ~75 days
 
7. Success Criteria
•	All 5 recon engines implement feature parity with legacy system
•	Test coverage ≥ 85% (unit + integration tests)
•	100% data parity: shadow-write results match legacy for 1M+ records
•	Performance: recon execution time within ±10% of legacy
•	API p99 response time < 500ms (monitored by Datadog)
•	Scheduled jobs: zero missed jobs in 2-week production trial
•	Zero unplanned rollbacks during customer migrations
•	Audit trail: all mutations logged with tenant_id, user_id, timestamp
•	Multi-tenancy: automated tests verify no cross-tenant data leaks
•	Deployment: GraalVM native image builds in <5 minutes
•	Complete runbooks and ops training completed
8. Conclusion
Greenfield rewrite in Java/Spring Boot provides a clean architecture from day one, eliminates legacy technical debt, and enables modern cloud-native patterns. The 30-week parallel migration strategy ensures zero downtime for customers and full rollback capability.

•	Key Benefits:
•	No in-place refactoring risk; complete architectural control
•	Parallel system allows full validation before customer cutover
•	Spring Boot ecosystem provides enterprise features out-of-the-box
•	Superior performance and native image support for cost savings
