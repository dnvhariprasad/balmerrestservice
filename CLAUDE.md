# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Before You Start

Read the `documentation/` folder first before starting any work. It contains important context about the system, APIs, and design decisions. Also refer to `testpy/` for existing Python test scripts that cover various API scenarios — useful for understanding endpoint behavior and testing new changes.

## Important: No Direct Database Access

Do NOT touch the database in any way — no JDBC, no stored procedures, no direct SQL. All data access must go through REST APIs (iBPS and OmniDocs). The existing JDBC/stored procedure code in `MovementRegistryRepository` is legacy and should not be extended or used as a pattern for new work.

## Build & Run Commands

```bash
# Start backend (kills existing process on port 8089, runs with Maven wrapper)
./run-server.sh
# Or directly:
./mvnw spring-boot:run -DskipTests

# Start frontend (Vite dev server on port 3000)
./run-frontend.sh

# Build WAR package
./mvnw clean package -DskipTests

# Run tests
./mvnw test
```

- **Java 11**, **Maven** (wrapper included), **WAR** packaging
- Backend runs on port **8089** (overridden from default 8080 in run script)
- Frontend is a Vite/React app in `frontend/` on port 3000

## Architecture

This is a **Spring Boot 2.5.4 middleware REST API** bridging a React frontend with two backend systems:

- **iBPS** (Newgen Business Process Management) — work item queues, process management, user authentication
- **OmniDocs** (Newgen Document Management) — document storage, checkout/checkin, annotations

All iBPS/OmniDocs communication happens via REST calls using `RestTemplate` (configured in `RestConfig` with 10s connect / 30s read timeouts).

### Base Package: `com.balmerlawrie.balmerrestservice`

### Layer Structure

| Layer | Package | Notes |
|-------|---------|-------|
| Controllers | `controller/` | REST endpoints, Swagger-annotated |
| Services | `service/` | Business logic; most extend `BaseIbpsService` |
| Repository | `repository/` | JDBC-based (`SimpleJdbcCall` for stored procedures) |
| DTOs | `dto/` | Request objects (`LoginRequest`, `NoteSheetRequest`) |
| Models | `model/` | `CachedSession` for session management |
| Config | `config/` | `SwaggerConfig`, `RestConfig`, `WebConfig` |
| Utilities | `util/` | `EncryptionUtil` (AES-128) |

### Key Services

- **`BaseIbpsService`** — Base class with shared iBPS integration: engine name retrieval, XML-to-JSON conversion. Most services inherit from this.
- **`SessionManager`** — Caches iBPS sessions per user with configurable timeout (default 25 min). Handles service account auto-authentication.
- **`NoteSheetService`** — Complex PDF generation: combines original notesheet, supporting docs table with hyperlink annotations, and comments. See `CREATEPDFNOTE_SERVICE.md` for details.
- **`DocumentOpsService`** — Document checkout/checkin, annotation filtering (preserves user annotations, removes old ViewLinks).
- **`MovementRegistryRepository`** — Routes stored procedure calls by process prefix (`office-`, `RTI-`, `legal-`, `finance-`).

### Session Flow

Authentication goes through iBPS `WMConnect` API. Sessions are cached in `SessionManager` and passed via `sessionId` header on subsequent requests.

### Response Pattern

All endpoints return JSON with `success`, `error`, `data` fields. Error responses are built via `createErrorResponse()` methods in services.

## Configuration

`src/main/resources/application.properties` — all external system URLs are parameterized:
- `ibps.base.url` — iBPS REST API base
- `omnidocs.base.url` — OmniDocs REST API base
- `db.server.host` / `db.server.port` — SQL Server connection
- `docs.viewer.base.url` — Document viewer URL
- `service.account.password` — AES-encrypted in properties, decrypted by `EncryptionUtil`

## HTML Templates

`src/main/resources/templates/` — Used for PDF generation (Flying Saucer renders HTML to PDF):
- `legal_printform.html` — Legal case print form with `{{PLACEHOLDER}}` substitution
- `document_list_template.html` / `document_row_template.html` — Supporting docs table
- `comment_template.html` / `comment_row_template.html` — Comments section

## Key Dependencies

- **PDF**: Flying Saucer, iText 2.1.7, OpenHTMLtoPDF, PDFBox 2.0.29
- **Word**: docx4j (6.1.2 & 8.3.7), Apache POI 5.2.5
- **XML/JSON**: Jackson XML, JAXB
- **API Docs**: springdoc-openapi-ui 1.7.0 (Swagger at `/swagger-ui.html`)
- **Database**: SQL Server via `mssql-jdbc`, Spring JDBC

## CORS

Configured in `WebConfig` for specific origins. When adding new allowed origins, update there.
