# EA Roadrunner Archive Insights Recon App - Complete Directory Documentation

## Table of Contents
1. [Project Overview](#project-overview)
2. [Architecture](#architecture)
3. [Directory Structure](#directory-structure)
4. [Core Components](#core-components)
5. [Deployment](#deployment)
6. [Testing](#testing)
7. [Configuration](#configuration)
8. [Scripts & Workflows](#scripts--workflows)

---

## Project Overview

**EA Roadrunner App** is a comprehensive reconciliation (recon) application bundled into a single PCF/Kubernetes application. It handles various reconciliation workflows for archive data across multiple customer environments including JPMC, Natwest, BNY, Deutsche Bank, and others.

### Key Features
- **Multi-tenant Support**: Handles multiple customer environments with different configurations
- **Automated Reconciliation**: Multiple recon strategies (Reactive, Generic, Hybrid, Simplified)
- **Health Monitoring**: Daily health checks and failure dashboards
- **State Management**: Automatic state correction for queued/failed messages
- **SFTP Integration**: File upload/download capabilities for customer data exchange
- **Web UI**: FastAPI-based web interface with OAuth authentication
- **Scheduled Tasks**: Celery-based task scheduling for automated workflows
- **Comprehensive Testing**: 95%+ code coverage with pytest

### Technology Stack
- **Backend**: Python 3.x, FastAPI, Celery
- **Database**: MongoDB, Elasticsearch
- **Message Queue**: Redis
- **Cloud**: AWS (S3, EC2), Kubernetes
- **Authentication**: Okta OAuth2/OIDC
- **Monitoring**: Datadog
- **Testing**: Pytest, Coverage, Mongomock, Testcontainers
- **Frontend**: Bootstrap, JavaScript, Jinja2 templates

---

## Architecture

### High-Level Architecture
```
┌─────────────────────────────────────────────────────────────┐
│                     FastAPI Web Server                       │
│  (OAuth Authentication, REST APIs, Web UI)                   │
└─────────────────┬───────────────────────────────────────────┘
                  │
┌─────────────────┴───────────────────────────────────────────┐
│                    Celery Task Queue                         │
│  (Scheduled Jobs, Async Processing)                          │
└─────────────────┬───────────────────────────────────────────┘
                  │
    ┌─────────────┼─────────────┐
    │             │             │
┌───▼────┐  ┌────▼─────┐  ┌───▼─────┐
│MongoDB │  │Elasticsearch│ │  Redis  │
└────────┘  └──────────┘  └─────────┘
    │             │             │
    └─────────────┼─────────────┘
                  │
    ┌─────────────┴─────────────┐
    │                           │
┌───▼─────┐            ┌───────▼────┐
│   S3    │            │    SFTP    │
│ Buckets │            │  Servers   │
└─────────┘            └────────────┘
```

### Design Documents
- **Main Design**: https://miro.com/app/board/o9J_leH6MOM=/
- **Reactive Recon Design**: https://miro.com/app/board/o9J_leH6MOM=/?moveToWidget=3074457366448999429

---

## Directory Structure

### Root Level
```
archive_insights_recon_app/
├── .coverage                    # Coverage data file
├── .coveragerc                  # Coverage configuration
├── .git/                        # Git repository
├── .github/                     # GitHub workflows and templates
├── .gitignore                   # Git ignore rules
├── .idea/                       # IDE configuration
├── .pytest_cache/               # Pytest cache
├── .venv/                       # Python virtual environment
├── Pipfile                      # Pipenv dependencies
├── README.md                    # Project README
├── cd/                          # Continuous Deployment configurations
├── ci/                          # Continuous Integration configurations
├── ea-roadrunner-java/          # Java components (if any)
├── file_path_mock/              # Mock file paths for testing
├── img.png                      # Documentation image
├── img_1.png                    # Documentation image
├── mocked/                      # Mock data
├── port.yml                     # Port configuration
├── requirements.txt             # Python dependencies
└── src/                         # Source code directory
```

---

## Core Components

### 1. Source Code Directory (`src/`)

#### Main Application Files
- **`main.py`** (815 lines)
  - FastAPI application entry point
  - OAuth2/OIDC authentication with Okta
  - Web UI routes and API endpoints
  - User session management
  - Script invocation endpoints
  - Dashboard rendering

- **`worker.py`** (788 lines)
  - Celery worker configuration
  - Task definitions for all recon scripts
  - Scheduled cron jobs
  - Redis broker configuration
  - Datadog metrics integration

#### Reconciliation Scripts

##### **`generic_recon.py`** (122,435 bytes)
The largest and most comprehensive recon script supporting:
- **Generic Recon Version 2**: Main reconciliation logic
- **Item Level Recon**: Granular item-level reconciliation
- **Feed Tracker Recon Report**: Tracking feed processing
- **Ageing Report**: Reports on aged unreconciled items
- **Missed EGW Messages**: Finds and uploads missed messages
- **Generic Source Recon**: Source-specific reconciliation
- Integration with S3, MongoDB, Elasticsearch
- CSV generation and SFTP upload
- Unreconciled data tracking in `recon_metrics_api` collection

##### **`reactive_recon.py`** (32,873 bytes)
Reactive reconciliation for JPMC environments:
- Copies unprocessed tar files from customer SFTP
- Untars and processes files
- Compares transcript_id status from Archive metrics
- Maintains unreconciled IDs in `recon_metrics_api`
- Uploads archived transcript_ids to customer SFTP

##### **`hybrid_recon.py`** (10,183 bytes)
Hybrid approach combining multiple recon strategies:
- Combines reactive and generic approaches
- Optimized for specific customer requirements

##### **`simplified_recon.py`** (9,296 bytes)
Simplified reconciliation for JPMC:
- Uploads all archived transcript_ids to customer SFTP
- CSV format output
- Minimal processing overhead

##### **`upload_all_archived.py`** (7,702 bytes)
Bulk upload functionality:
- Retrieves all archived transcript_ids
- Copies to customer SFTP location
- CSV format generation

##### **`internal_reactive_recon_update_db.py`** (15,691 bytes)
Internal reconciliation database updates:
- Reads non-archived transcript_ids from archive_metrics
- Updates recon_metrics_api collection
- Used for environments except JPMC and Natwest

#### State Management & Correction

##### **`stateCorrector.py`** (5,598 bytes)
Automated state correction:
- Runs daily to verify Queued, Fatal, Reprocessing messages
- Checks iDoc and object store
- Automatically marks as Archived/Duplicate in Archive Metrics
- Default processing window: 10 days before to 3 days before current date
- Executes daily at 9 AM PST

#### Health Checks & Monitoring

##### **`Daily_Health_Checks.py`** (28,629 bytes)
Comprehensive daily health check reporting:
- **CSV/LDIF Import Status**: File details, participants created/updated
- **Search Metrics**: Total searches and documents returned
- **Disposition**: Content source, network, disposed count
- **Security Reporting**: User creation, role changes, failed attempts
- **Ingestion Stats**: Received, archived, failed counts (>24h and <24h)
- **Size Metrics**: Large payloads, attachments, email sizes
- **Failed Count Analysis**: Breakdown by age and content source
- **Archived Raw Count**: EGW Raw EML and Alcatraz Raw XML
- **Exports**: Current and past 24-hour export statistics
- Email notifications to support team

##### **`archive_metrics_failure_dashbord.py`** (20,340 bytes)
Failure dashboard and metrics:
- Queries recon_metrics_api for unreconciled transcript_ids
- Pushes data counts to Datadog dashboard
- Dashboard: https://app.datadoghq.com/dashboard/abt-wki-arz/ea-archive-metrics-failure-dashboard

#### Aggregate Reporting

##### **`recon_metrics_aggregate.py`** (83,948 bytes)
Comprehensive aggregate metrics:
- Aggregates reconciliation metrics across environments
- Generates detailed reports
- Historical trend analysis
- Multi-dimensional data aggregation

##### **`recon_metrics_aggregate_report_source.py`** (32,462 bytes)
Source-specific aggregate reporting:
- Aggregate metrics by source
- CNR (Could Not Receive) message removal
- SFTP cleanup (removes CSV files older than 90 days)

#### API Integration Scripts

##### **`invoke_jira_automation.py`** (40,037 bytes)
JIRA integration and automation:
- Archive metrics failure tracking
- SINC collection updates
- Auto-incident dashboard
- Daily incident creation and management

##### **`invoke_recon_analysis_api.py`** (8,805 bytes)
Recon analysis API invocation:
- Triggers recon analysis workflows
- API endpoint management

##### **`invoke_integrity_check_api.py`** (5,491 bytes)
Integrity check API integration:
- Data integrity verification
- Consistency checks

##### **`invoke_index_gateway_api.py`** (7,869 bytes)
Index gateway API calls:
- Indexing operations
- Gateway communication

##### **`invoke_archive_raw_api.py`** (9,130 bytes)
Archive raw data API:
- Raw data archival operations
- API endpoint management

##### **`callremediation_api.py`** (5,447 bytes)
Remediation API calls:
- Automated remediation workflows
- Error correction

##### **`ingestion_reprocess_api.py`** (9,602 bytes)
Ingestion reprocessing:
- Reprocess failed ingestions
- Retry logic

#### Specialized Scripts

##### **`generate_dlp_reports.py`** (37,695 bytes)
DLP (Data Loss Prevention) reporting:
- Generates comprehensive DLP reports
- Compliance reporting
- Data classification metrics

##### **`csurv_report.py`** (19,241 bytes)
CSURV reporting for Natwest:
- Customer-specific surveillance reports
- Regulatory compliance reporting

##### **`nw_recon_reprocess.py`** (3,891 bytes)
Natwest recon reprocessing:
- Reprocesses archived data
- Natwest-specific logic

##### **`sanity_run.py`** (8,814 bytes)
Sanity check execution:
- Environment sanity checks
- Pre-deployment validation

##### **`stored_indexSkip_check.py`** (7,119 bytes)
Stored index skip state verification:
- Checks stored messages
- Index skip state updates

##### **`upload_files_to_sftp.py`** (13,637 bytes)
SFTP file upload utility:
- Generic file upload to SFTP
- Group ID information updates
- Connection management

#### Configuration & Utilities

##### **`archive_metrics_scripts.py`** (3,903 bytes)
Archive metrics utility scripts:
- Helper functions for archive metrics
- Not archived message tracking

##### **`archive_metrics_update_scripts.py`** (13,865 bytes)
Archive metrics update operations:
- Bulk update operations
- State transitions

##### **`oauth_token_generator.py`** (1,985 bytes)
OAuth token generation:
- Token generation for API calls
- OAuth flow management

##### **`eventlogger_fetch.py`** (4,353 bytes)
Event logger data fetching:
- Retrieves event logs
- Log aggregation

##### **`invoke_config_manager.py`** (2,316 bytes)
Configuration management:
- Dynamic configuration loading
- Environment-specific configs

##### **`invoke_stored_dashboard.py`** (5,193 bytes)
Stored messages dashboard:
- Dashboard data aggregation
- Metrics visualization

##### **`recon_metrics_api_ic_remediation.py`** (8,256 bytes)
Integrity check remediation:
- IC failure remediation
- Automated fixes

### 2. Common Utilities (`src/common/`)

#### **`utils.py`** (13,573 bytes)
General utility functions:
- CSV comparison (`compare_n_csv`)
- Date/time utilities
- String manipulation
- Data transformation helpers

#### **`mongo_connection.py`** (2,030 bytes)
MongoDB connection management:
- Connection pooling
- Database client initialization
- Error handling

#### **`es_client.py`** (9,546 bytes)
Elasticsearch client:
- ES connection management
- Query builders
- Index operations

#### **`s3_client.py`** (1,902 bytes)
AWS S3 client:
- S3 bucket operations
- File upload/download
- Boto3 integration

#### **`sftp_connection.py`** (3,892 bytes)
SFTP connection management:
- Paramiko-based SFTP client
- Connection pooling
- File transfer operations

#### **`csv_operations.py`** (9,209 bytes)
CSV file operations:
- CSV reading/writing
- Data parsing
- Format validation

#### **`smtp_mail_attachment.py`** (4,347 bytes)
Email with attachments:
- SMTP client
- Attachment handling
- Email composition

#### **`smtp_mail_html.py`** (2,386 bytes)
HTML email sending:
- HTML email templates
- SMTP delivery

#### **`datadog_send_metrics.py`** (3,367 bytes)
Datadog metrics integration:
- Metric submission
- Custom metrics
- Tag management

#### **`billing_api_client.py`** (8,206 bytes)
Billing API client:
- Billing data retrieval
- API integration

#### **`event_logging_api_client.py`** (7,267 bytes)
Event logging API:
- Event log submission
- API client

#### **`datetime_last24hours.py`** (1,593 bytes)
Date/time utilities:
- Last 24 hours calculation
- Time range helpers

#### **`constants.py`** (160 bytes)
Application constants:
- Global constants
- Configuration values

### 3. Data Access Layer (`src/data_access_layer/`)

MongoDB collection models and data access objects:

#### Collection Models
- **`archive_metrics.py`**: Archive metrics collection
- **`recon_metrics_api.py`**: Main recon metrics API collection
- **`recon_metrics_api_aggregate.py`**: Aggregated metrics
- **`recon_metrics_api_sinc.py`**: SINC-specific metrics
- **`recon_metrics_cnr.py`**: CNR (Could Not Receive) messages
- **`recon_metrics_egw_missed_tracker.py`**: Missed EGW messages
- **`recon_metrics_file_tracker.py`**: File tracking
- **`recon_metrics_ic_failures.py`**: Integrity check failures
- **`recon_metrics_failure_anlaysis.py`**: Failure analysis
- **`recon_metrics_system_health.py`**: System health metrics
- **`recon_metrics_ui.py`**: UI-specific data
- **`recon_app_tracker.py`**: Application tracking
- **`recon_scripts_history.py`**: Script execution history
- **`archive_metrics_update_tracker.py`**: Update tracking
- **`auto_remediation_ic_report.py`**: Auto-remediation reports
- **`compliance_audit_deleted.py`**: Compliance audit data
- **`egw_archived_raw_reprocess.py`**: EGW raw reprocessing
- **`endpoint.py`**: Endpoint configuration
- **`failed_config.py`**: Failed configuration tracking
- **`index_metadata.py`**: Index metadata
- **`indexing_skip_config.py`**: Index skip configuration
- **`job_request_config.py`**: Job request configuration
- **`storage_archive_disposed_report.py`**: Disposed data reports
- **`tenancy.py`**: Tenancy information

#### **`read_me.py`** (810 bytes)
Data access layer documentation and examples

### 4. Daily Health Check Modules (`src/dhc_archive/`)

Modular health check components:

#### **`Alcatraz_Archive_Raw/`**
- Alcatraz raw XML archival metrics
- Raw data processing stats

#### **`CsvImportStatus/`**
- CSV/LDIF import status reporting
- Participant creation/update tracking

#### **`Egw_Archive_Raw/`**
- EGW raw EML archival metrics
- Raw message processing

#### **`Exceptions/`**
- Exception tracking and reporting
- Error categorization

#### **`Exports/`**
- Export job monitoring
- Current and historical export stats

#### **`FatalMessgesCount/`**
- Fatal message counting
- Critical failure tracking

#### **`Ingestion_Queries/`**
- Ingestion statistics queries
- Received, archived, failed counts

#### **`SearchMetrics/`**
- Search performance metrics
- Document retrieval statistics

#### **`Security/`**
- Security event reporting
- User activity tracking
- Failed login attempts

#### **`README.md`** (4,196 bytes)
DHC module documentation

### 5. Properties & Configuration (`src/properties/`)

#### **`prop.py`** (32,446 bytes)
Main properties and settings:
- Environment-specific configurations
- Database connection strings
- API endpoints
- Feature flags
- Customer-specific settings
- Datadog configuration
- SFTP credentials
- Email configurations

#### **`common_config.ini`** (3,569 bytes)
Common configuration file:
- Tenant-specific settings
- Database hosts
- Elasticsearch endpoints
- S3 bucket names
- SFTP server details

#### **`recon_team.config`** (471 bytes)
Recon team configuration:
- Team member email addresses
- Access control lists

#### **`integrationtest_properties.py`** (395 bytes)
Integration test properties:
- Test environment configurations
- Mock data settings

### 6. Web UI (`src/templates/` & `src/static/`)

#### Templates (`src/templates/`)
- **`login.html`**: OAuth login page
- **`index.html`**: Main dashboard
- **`scripts.html`**: Script execution interface
- **`recon_dashboard.html`**: Recon metrics dashboard
- **`reactive_recon_results.html`**: Reactive recon results
- **`reactive_recon_detailed_results.html`**: Detailed results view
- **`cron_results.html`**: Scheduled job results

#### Static Assets (`src/static/`)
- **`bootstrap.min.css`**: Bootstrap CSS framework
- **`bootstrap.min.js`**: Bootstrap JavaScript
- **`popper.min.js`**: Popper.js for tooltips
- **`main.css`**: Custom styles
- **`common.js`**: Common JavaScript utilities
- **`reactive_recon.js`**: Reactive recon UI logic
- **`smarsh-logo-new.svg`**: Company logo
- **`logout_icon.png`**: Logout icon

### 7. Testing (`src/tests/`)

Comprehensive test suite with 95%+ coverage:

#### Unit Tests (41 test files)
- **`test_Daily_Health_Checks.py`** (23,758 bytes)
- **`test_archive_metrics_failure_dashboard.py`** (45,897 bytes)
- **`test_archive_metrics_scripts.py`** (3,765 bytes)
- **`test_archive_metrics_update_scripts.py`** (13,620 bytes)
- **`test_billing_api_client.py`** (20,459 bytes)
- **`test_callremediation_api.py`** (9,072 bytes)
- **`test_csurv_report.py`** (13,683 bytes)
- **`test_egw_archive_raw_migration.py`** (21,739 bytes)
- **`test_event_logging_api_client.py`** (19,180 bytes)
- **`test_generate_dlp_reports.py`** (4,674 bytes)
- **`test_generate_dlp_reports_billing_api.py`** (14,043 bytes)
- **`test_generic_recon.py`** (94,523 bytes) - Largest test file
- **`test_hybrid_recon.py`** (13,156 bytes)
- **`test_ingestion_reprocess_api.py`** (9,659 bytes)
- **`test_internal_reactive_recon_update_db.py`** (12,725 bytes)
- **`test_invoke_archive_raw_api.py`** (13,688 bytes)
- **`test_invoke_config_manager.py`** (3,253 bytes)
- **`test_invoke_index_gateway_api.py`** (8,019 bytes)
- **`test_invoke_integrity_check_api.py`** (5,571 bytes)
- **`test_invoke_jira_automation.py`** (51,471 bytes)
- **`test_invoke_recon_analysis_api.py`** (19,602 bytes)
- **`test_invoke_stored_dashboard.py`** (4,573 bytes)
- **`test_nwm_recon_reprocess.py`** (1,793 bytes)
- **`test_oauth_token_generator.py`** (1,380 bytes)
- **`test_reactive_recon.py`** (15,600 bytes)
- **`test_recon_app_tracker_archived_disposed.py`** (4,569 bytes)
- **`test_recon_metrics_aggregate.py`** (96,965 bytes) - Largest test file
- **`test_recon_metrics_aggregate_report_jpmc.py`** (30,510 bytes)
- **`test_recon_metrics_api_ic_remediation.py`** (12,601 bytes)
- **`test_sanity_run.py`** (11,636 bytes)
- **`test_simplified_recon.py`** (5,629 bytes)
- **`test_stateCorrector.py`** (8,576 bytes)
- **`test_stored_indexSkip_check.py`** (6,971 bytes)
- **`test_upload_all_archived.py`** (7,153 bytes)
- **`test_upload_files_to_sftp.py`** (4,725 bytes)

#### Test Utilities
- **`mocks/`**: Mock objects and data
- **`integration_tests/`**: Integration test suite
- **`read_me.py`**: Testing documentation

#### Test Configuration
- **`.coveragerc`**: Coverage configuration
- **`.coverage`**: Coverage data file
- **`.pytest_cache/`**: Pytest cache

---

## Deployment

### 1. Kubernetes Deployment (`cd/k8s/`)

#### Base Configuration (`cd/k8s/base/`)
- **`deployment.yaml`**: Main deployment specification
- **`service.yaml`**: Kubernetes service definition
- **`serviceaccount.yaml`**: Service account configuration
- **`init-containers.yaml`**: Init container patches
- **`env-variables.yaml`**: Environment variable patches
- **`kustomization.yaml`**: Kustomize configuration

#### Overlays (`cd/k8s/overlays/`)
Environment-specific configurations organized by:
- **Cloud Provider**: AWS, GCP, Azure
- **Region**: us-east-1, eu-west-2, eu-central-1, ap-southeast-1, etc.
- **Customer/Environment**: 
  - JPMC (NAM, EMEA, APAC, Swiss)
  - Natwest (Prod, UAT)
  - BNY (Prod APAC, Prod NAM, Prod EMEA)
  - Deutsche Bank
  - RBC
  - Bungee
  - DTCC
  - Schwab
  - Putnam
  - Principal
  - Hartford
  - SMBC
  - Santander POC

Example structure:
```
overlays/aws/us-east-1/ep-rbc-prod/
├── common/
│   └── kustomization.yaml
└── rbcuat/
    └── kustomization.yaml
```

#### Templates (`cd/k8s/templates/`)
Reusable Kubernetes templates:
- Deployment templates
- Service templates
- ConfigMap templates
- Secret templates
- Ingress templates

### 2. Docker Configuration

#### **`src/Dockerfile`**
```dockerfile
FROM smarshops/py_recon_scripts_app:2.1
WORKDIR /usr/src/app
COPY . .
CMD ["sh", "/usr/src/app/start.sh"]
```

#### **`ci/Dockerfile`**
CI/CD specific Dockerfile for build pipelines

#### **`src/start.sh`**
Startup script for the application

### 3. CI/CD

#### GitHub Actions (`.github/workflows/`)
- Automated testing
- Docker image building
- Deployment pipelines

#### **`.github/CODEOWNERS`**
Code ownership and review requirements

#### **`.github/PULL_REQUEST_TEMPLATE.md`**
PR template for consistent reviews

### 4. Deployment Pipeline
External repository: https://github.com/Smarsh/archive_insights_recon_scripts_pipelines

---

## Configuration

### Environment Variables

Key environment variables (from `cd/k8s/base/kustomization.yaml`):

#### Application Configuration
- `SV_COMPONENT`: ea-roadrunner
- `SV_SERVICE_TEAM`: archive-insights
- `TENANT_NAME`: Customer/environment identifier
- `RECON_TYPE`: Type of recon (generic_recon, hybrid_recon, etc.)
- `IS_DEV`: Development mode flag

#### Authentication
- `OIDC_BASE_URL`: Okta OAuth base URL
- `OIDC_CLIENT_ID`: OAuth client ID
- `OIDC_CLIENT_SECRET`: OAuth client secret
- `OIDC_REDIRECT_URL`: OAuth redirect URL

#### Telemetry & Monitoring
- `TELEMETRY_PROVIDER`: OTEL
- `DD_TRACE_ENABLED`: false
- `HONEYCOMB_DATASET`: ea-roadrunner
- `DATADOG_API_KEY`: Datadog API key

#### Database Configuration
- `MONGO_HOST`: MongoDB host
- `MONGO_PORT`: MongoDB port
- `MONGO_DB`: Database name
- `ES_ARCHIVE_HOST`: Elasticsearch host
- `ES_ARCHIVE_HOST_USER`: ES username
- `ES_ARCHIVE_HOST_PASSWD`: ES password

#### Performance Tuning
- `THREAD_POOL_SIZE`: 3
- `SERVER_CONNECTIONTIMEOUT`: 600000
- `TIMEOUT_IN_SECONDS`: 600
- `JETTY_IDLE_TIMEOUT`: 600000
- `AM_CHUNK_SIZE`: 1000
- `CHUNK_SIZE`: 30000

#### Feature Flags
- `OLD_DHC_REPORT_ENABLE`: false
- `SFTP_HEALTH_CHECK_ENABLED`: true

#### Recon Configuration
- `STATECORRECTOR_STATES`: "Queued,Fatal,Reprocessing,Stored,Failed_Parked,Failed"
- `DHC_MAIL_HOUR`: 9
- `METRIC_NAME_NOT_ARCHIVE`: smarsh.ea.roadrunner.archive_insights.not_archived

#### Communication
- `SLACK_EMAIL_ID`: ea-archive_insights-a-aaaacqnxpktsn7gdofhwlmtvz4@smarsh.slack.com

### Configuration Files

#### **`src/common_config.ini`**
Environment-specific configurations:
- MongoDB connection strings
- Elasticsearch endpoints
- S3 bucket names
- SFTP server details
- Customer-specific settings

#### **`src/recon_team.config`**
Team member email addresses for notifications and access control

#### **`port.yml`**
Port configuration for local development

---

## Scripts & Workflows

### Reconciliation Workflows

#### 1. **Reactive Recon** (JPMC Environments)
**Schedule**: Daily  
**Script**: `reactive_recon.py`

**Workflow**:
1. Copy unprocessed tar file from customer SFTP
2. Untar and process file
3. Compare transcript_id status from Archive metrics
4. Store unreconciled IDs in recon_metrics_api
5. Generate CSV of archived transcript_ids
6. Upload CSV to customer SFTP

**Design**: https://miro.com/app/board/o9J_leH6MOM=/?moveToWidget=3074457366448999429

#### 2. **Generic Recon** (Natwest Environments)
**Schedule**: Every 4 hours  
**Script**: `generic_recon.py`

**Workflow**:
1. Query data from S3 bucket (Cloud Capture & EGW folders)
2. Query archive metrics with transcript_id
3. If state is Archived:
   - Create CSV with EA_Archived naming convention
   - Copy CSV to EA_Archived folder in S3
4. Maintain unreconciled data in recon_metrics_api
5. Periodically check for newly archived transcript_ids
6. Process and delete reconciled records

**Display Name States**:
- **Unreconciled**: Failed/Fatal/Failed_Bulk/Failed_Parked/Failed_Post_Indexing
- **Rejected**: Rejected state
- **Not Received**: Not_Received state

#### 3. **Hybrid Recon**
**Schedule**: Configurable  
**Script**: `hybrid_recon.py`

Combines reactive and generic approaches for optimized reconciliation.

#### 4. **Simplified Recon** (JPMC Environments)
**Schedule**: Daily  
**Script**: `simplified_recon.py`

Uploads all archived transcript_ids to customer SFTP in CSV format.

#### 5. **Upload All Archived** (JPMC Environments)
**Schedule**: Daily  
**Script**: `upload_all_archived.py`

Picks all archived transcript_ids and copies to customer SFTP in CSV format.

#### 6. **Internal Reactive Recon** (Except JPMC & Natwest)
**Schedule**: Configurable  
**Script**: `internal_reactive_recon_update_db.py`

Reads non-archived transcript_ids from archive_metrics and updates recon_metrics_api.

### State Management

#### **State Corrector**
**Schedule**: Daily at 9 AM PST  
**Script**: `stateCorrector.py`

**Workflow**:
1. Start date: 10 days before current date
2. End date: 3 days before current date
3. Processing states: Queued, Fatal, Reprocessing
4. Verify messages in iDoc & object store
5. Auto-mark as Archived/Duplicate in Archive Metrics

### Health & Monitoring

#### **Daily Health Check - DHC Archived**
**Schedule**: Daily  
**Script**: `Daily_Health_Checks.py`

**Reports**:
- CSV/LDIF import status
- Searches performed
- Disposition metrics
- Security reporting
- Ingestion statistics
- Size metrics (large payloads, attachments)
- Failed count analysis (>24h and <24h)
- Archived raw count
- Export statistics

**Email**: Sends detailed report to support team

#### **Archive Metrics Failure Dashboard**
**Schedule**: Daily  
**Script**: `archive_metrics_failure_dashbord.py`

Queries recon_metrics_api and pushes counts to Datadog dashboard.

**Dashboard**: https://app.datadoghq.com/dashboard/abt-wki-arz/ea-archive-metrics-failure-dashboard

### Aggregate Reporting

#### **Aggregate Recon Metrics**
**Schedule**: Configurable  
**Script**: `recon_metrics_aggregate.py`

Aggregates reconciliation metrics across environments for comprehensive reporting.

#### **Aggregate Recon Metrics by Source**
**Schedule**: Configurable  
**Script**: `recon_metrics_aggregate_report_source.py`

Source-specific aggregate reporting with CNR message removal and SFTP cleanup.

### Specialized Workflows

#### **CSURV Report** (Natwest)
**Script**: `csurv_report.py`

Generates customer surveillance reports for regulatory compliance.

#### **DLP Reports**
**Script**: `generate_dlp_reports.py`

Generates Data Loss Prevention reports for compliance.

#### **Sanity Run**
**Script**: `sanity_run.py`

Environment sanity checks and pre-deployment validation.

#### **Stored Index Skip Check**
**Script**: `stored_indexSkip_check.py`

Verifies stored messages and updates index skip states.

---

## Testing

### Test Execution

#### Run All Tests
```bash
pytest
```

#### Run with Coverage
```bash
coverage run -m pytest
coverage report -m
coverage html
```

#### View Coverage Report
```bash
open htmlcov/index.html
```

### Test Structure

#### Unit Tests
- Mock external dependencies (MongoDB, Elasticsearch, S3, SFTP)
- Use `mongomock` for MongoDB mocking
- Use `testcontainers` for integration tests
- Comprehensive assertions and edge case coverage

#### Integration Tests
Located in `src/tests/integration_tests/`:
- End-to-end workflow testing
- Real database interactions (test containers)
- API integration testing

#### Mock Data
Located in `src/tests/mocks/`:
- Sample CSV files
- Mock API responses
- Test fixtures

### Coverage Goals
- **Target**: 95%+ code coverage
- **Current**: Maintained through CI/CD
- **Report**: Generated on every test run

---

## Environment Links

### Production Environments

#### JPMC
- **NAM Prod**: https://ea-roadrunner-prodnam.apps.us-east-1.aws.smarsh.cloud
- **APAC Prod**: https://ea-roadrunner-prodapac.ea.jpapac.us-east-1.aws.smarsh.cloud/
- **EMEA Prod**: https://ea-roadrunner-prodemea.ea-internal.jpmc.eu-central-1.aws.smarsh.cloud/
- **Swiss Prod**: https://ea-roadrunner-prodswiss.ea-internal.jpmc.eu-central-2.aws.smarsh.cloud/
- **APAC Sec**: https://ea-roadrunner-prodapac.apps.sec.apac.jpmc.smarsh.cloud
- **EMEA Sec**: https://ea-roadrunner-prodemea.apps.sec.emea.jpmc.smarsh.cloud
- **EMEA Pri**: https://ea-roadrunner-prodemea.apps.pri.emea.jpmc.smarsh.cloud
- **APAC Pri**: https://ea-roadrunner-prodapac.apps.pri.apac.jpmc.smarsh.cloud

#### BNY
- **NAM US East**: https://ea-roadrunner-eaprod.nam.bnym.smarsh.cloud
- **APAC South East**: https://ea-roadrunner-bnymprodapac.apps.ap-southeast-1.aws.smarsh.cloud
- **EU Central**: https://ea-roadrunner-bnymprodemea.apps.eu-central-1.aws.smarsh.cloud

#### Natwest
- **Prod EU-West**: https://ea-roadrunner-natwest.apps.eu-west-2.aws.smarsh.cloud
- **Prod EU Central**: https://ea-roadrunner-natwest-natwest.ea.mt.eu-central-1.aws.smarsh.cloud
- **UAT EU-West**: https://ea-roadrunner-natwestuat.apps.eu-west-2.aws.smarsh.cloud

#### Other Customers
- **Bungee**: https://ea-roadrunner-bunge-bunge.ca.smarsh.cloud
- **DTCC**: https://ea-roadrunner-dtcc-dtccprod.ca.smarsh.cloud
- **Schwab**: https://ea-roadrunner-archive.schwab.smarsh.cloud
- **Deutsche Bank**: https://ea-roadrunner-deutschebankprod.ea.mt.eu-central-1.aws.smarsh.cloud
- **Putnam**: https://ea-roadrunner-putnam-putnamarchive.ca.smarsh.cloud/
- **Principal**: https://ea-roadrunner-principal-principal.ca.smarsh.cloud/
- **Hartford**: https://ea-roadrunner-higsmarsheap-higsmarsheap.ea.mt.us-east-1.aws.smarsh.cloud/
- **SMBC**: https://ea-roadrunner-smbcprod.ea-internal.smbc.us-east-1.aws.smarsh.cloud/
- **Santander POC**: https://ea-roadrunner-santanderpoc-santanderpoc.ea-internal.mt.eu-central-1.aws.smarsh.cloud/

#### Test/Demo
- **Demo (cimsea)**: https://ea-roadrunner-demo-cimsea.apps.prod.smarsh.cloud
- **US West2 (Sanity1)**: https://ea-roadrunner-sanity1.ea.staging.us-west-2.aws.smarsh.cloud

---

## Local Development Setup

### Prerequisites
- Docker installed
- Python 3.x
- Git

### Setup Steps

1. **Clone Repository**
   ```bash
   git clone <repository_url>
   cd archive_insights_recon_app
   ```

2. **Update Configuration**
   ```bash
   vim src/common_config.ini
   # Update with respective environment IPs, credentials, etc.
   ```

3. **Build Docker Image**
   ```bash
   cd src
   docker build -t ea-roadrunner:latest .
   ```

4. **Run Docker Container**
   ```bash
   docker run --name ea-roadrunner \
     -p 8080:8080 \
     -e IS_DEV=True \
     -d ea-roadrunner:latest
   ```

5. **Access UI**
   ```
   http://localhost:8080/
   ```

### Development Without Docker

1. **Create Virtual Environment**
   ```bash
   python3 -m venv .venv
   source .venv/bin/activate
   ```

2. **Install Dependencies**
   ```bash
   pip install -r requirements.txt
   ```

3. **Run Application**
   ```bash
   cd src
   export IS_DEV=True
   uvicorn main:app --reload --port 8080
   ```

4. **Run Celery Worker**
   ```bash
   celery -A worker.celery worker --loglevel=info
   ```

5. **Run Celery Beat (Scheduler)**
   ```bash
   celery -A worker.celery beat --loglevel=info
   ```

---

## Dependencies

### Core Dependencies
- **paramiko** (3.4.0): SSH/SFTP operations
- **pytest** (8.2.2): Testing framework
- **mypy** (1.10.0): Static type checking
- **numpy** (1.26.4): Numerical operations
- **aiofiles** (23.2.1): Async file operations
- **celery** (5.3.6): Distributed task queue
- **fastapi** (0.110.2): Web framework
- **Jinja2** (3.1.3): Template engine
- **requests** (2.31.0): HTTP client
- **uvicorn** (0.30.1): ASGI server
- **boto3** (1.34.113): AWS SDK
- **redis** (5.0.4): Redis client
- **pymongo** (4.7.2): MongoDB driver
- **datadog** (0.49.0): Datadog integration
- **python-multipart** (0.0.9): Multipart form data
- **elasticsearch** (7.14.1): Elasticsearch client
- **fastapi-login** (1.8.0): FastAPI authentication
- **okta-jwt-verifier** (0.2.2): Okta JWT verification
- **pandas** (2.2.2): Data analysis
- **pysftp** (0.2.9): SFTP client (deprecated, uses paramiko)
- **csv-diff** (1.2): CSV comparison
- **mongomock** (4.1.2): MongoDB mocking
- **coverage** (7.5.3): Code coverage
- **coloredlogs** (15.0.1): Colored logging
- **testcontainers** (4.10.0): Container-based testing
- **pdfkit** (1.0.0): PDF generation
- **matplotlib** (3.8.4): Plotting
- **cryptography** (42.0.7): Cryptographic operations
- **setuptools** (>=68.0.0, <81): Package management

---

## Data Flow

### Reconciliation Data Flow
```
Customer SFTP/S3
       │
       ▼
┌──────────────┐
│ Recon Script │
└──────┬───────┘
       │
       ├─────────────────┐
       │                 │
       ▼                 ▼
┌─────────────┐   ┌──────────────┐
│Archive      │   │Elasticsearch │
│Metrics (ES) │   │(iDoc/Object) │
└──────┬──────┘   └──────┬───────┘
       │                 │
       └────────┬────────┘
                │
                ▼
       ┌────────────────┐
       │  Comparison    │
       │    Logic       │
       └────────┬───────┘
                │
       ┌────────┴────────┐
       │                 │
       ▼                 ▼
┌─────────────┐   ┌──────────────┐
│ Reconciled  │   │ Unreconciled │
│   (CSV)     │   │  (MongoDB)   │
└──────┬──────┘   └──────────────┘
       │
       ▼
Customer SFTP/S3
```

### Health Check Data Flow
```
┌──────────────┐
│ Scheduled    │
│ Celery Task  │
└──────┬───────┘
       │
       ├─────────────────┬─────────────────┬──────────────┐
       │                 │                 │              │
       ▼                 ▼                 ▼              ▼
┌─────────────┐   ┌─────────────┐   ┌──────────┐   ┌─────────┐
│  MongoDB    │   │Elasticsearch│   │    S3    │   │  SFTP   │
└──────┬──────┘   └──────┬──────┘   └────┬─────┘   └────┬────┘
       │                 │                │              │
       └────────┬────────┴────────────────┴──────────────┘
                │
                ▼
       ┌────────────────┐
       │  Data          │
       │  Aggregation   │
       └────────┬───────┘
                │
                ├────────────────┬────────────────┐
                │                │                │
                ▼                ▼                ▼
       ┌────────────┐   ┌────────────┐   ┌──────────────┐
       │   Email    │   │  Datadog   │   │   MongoDB    │
       │   Report   │   │  Metrics   │   │  (History)   │
       └────────────┘   └────────────┘   └──────────────┘
```

---

## Security

### Authentication
- **OAuth2/OIDC**: Okta integration for user authentication
- **JWT Tokens**: ID tokens and access tokens
- **Session Management**: Cookie-based sessions

### Authorization
- **Role-Based Access**: Recon team members have elevated privileges
- **Script Invocation Control**: Only authorized users can invoke scripts

### Secrets Management
- **Environment Variables**: Sensitive data stored as env vars
- **Kubernetes Secrets**: Credentials stored in K8s secrets
- **No Hardcoded Credentials**: All credentials externalized

### Network Security
- **HTTPS**: All production endpoints use HTTPS
- **SFTP**: Secure file transfers
- **VPC**: Kubernetes clusters in private VPCs

---

## Monitoring & Observability

### Datadog Integration
- **Metrics**: Custom metrics for recon status
- **Dashboards**: Archive Metrics Failure Dashboard
- **Alerts**: Automated alerting on failures

### Logging
- **Structured Logging**: JSON-formatted logs
- **Log Levels**: Configurable (INFO, WARN, ERROR)
- **Colored Logs**: coloredlogs for development

### Health Checks
- **Readiness Probe**: `/health/ready` endpoint
- **Liveness Probe**: Kubernetes liveness checks
- **SFTP Health Check**: Periodic SFTP connection verification

### Telemetry
- **OpenTelemetry**: OTEL integration
- **Honeycomb**: Distributed tracing
- **Trace Sampling**: Configurable sampling rates

---

## Troubleshooting

### Common Issues

#### 1. SFTP Connection Failures
- Check SFTP credentials in configuration
- Verify network connectivity
- Check SFTP server availability
- Review SFTP health check logs

#### 2. MongoDB Connection Issues
- Verify MongoDB host and port
- Check authentication credentials
- Ensure network access to MongoDB
- Review connection pool settings

#### 3. Elasticsearch Query Failures
- Check ES host configuration
- Verify index names
- Review query syntax
- Check ES cluster health

#### 4. Celery Task Failures
- Check Redis connectivity
- Review task logs
- Verify task configuration
- Check worker status

#### 5. OAuth Authentication Issues
- Verify Okta configuration
- Check client ID and secret
- Review redirect URL
- Validate JWT tokens

### Debug Mode
Set `IS_DEV=True` for development mode with:
- Detailed error messages
- Local configuration loading
- Development OAuth settings

### Log Locations
- **Application Logs**: `src/logs/`
- **Celery Logs**: Stdout/stderr
- **Test Logs**: `src/log/`

---

## Contributing

### Code Style
- Follow PEP 8 guidelines
- Use type hints
- Write docstrings for functions
- Maintain test coverage >95%

### Pull Request Process
1. Create feature branch
2. Write tests for new functionality
3. Ensure all tests pass
4. Update documentation
5. Submit PR using template
6. Address review comments
7. Merge after approval

### Code Review
- Required reviewers defined in `.github/CODEOWNERS`
- All PRs require approval
- CI/CD checks must pass

---

## Maintenance

### Regular Tasks
- **Daily**: Monitor health check emails
- **Weekly**: Review Datadog dashboards
- **Monthly**: Update dependencies
- **Quarterly**: Security audits

### Dependency Updates
```bash
pip list --outdated
pip install --upgrade <package>
pip freeze > requirements.txt
```

### Database Maintenance
- **MongoDB**: Regular backups
- **Elasticsearch**: Index lifecycle management
- **Redis**: Memory monitoring

---

## Support

### Team Contacts
- **Recon Team**: Configured in `recon_team.config`
- **Slack**: ea-archive_insights-a-aaaacqnxpktsn7gdofhwlmtvz4@smarsh.slack.com

### Documentation
- **Miro Board**: https://miro.com/app/board/o9J_leH6MOM=/
- **Datadog Dashboard**: https://app.datadoghq.com/dashboard/abt-wki-arz/ea-archive-metrics-failure-dashboard
- **Deployment Pipeline**: https://github.com/Smarsh/archive_insights_recon_scripts_pipelines

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

### Acronyms
- **JPMC**: JPMorgan Chase
- **BNY**: Bank of New York Mellon
- **DTCC**: Depository Trust & Clearing Corporation
- **RBC**: Royal Bank of Canada
- **SMBC**: Sumitomo Mitsui Banking Corporation

### File Size Reference
- Largest source file: `generic_recon.py` (122,435 bytes)
- Largest test file: `test_recon_metrics_aggregate.py` (96,965 bytes)
- Total source files: 172 items in `src/`
- Total test files: 41 test files

---

**Document Version**: 1.0  
**Last Updated**: 2026-06-08  
**Maintained By**: Archive Insights Team
