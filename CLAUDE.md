# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Unity Catalog is the industry's only universal catalog for data and AI, supporting multimodal interfaces for any format, engine, and asset. This repository contains:

- **Core Unity Catalog Server**: Java/Scala-based catalog server with REST APIs
- **AI Integration Libraries**: Python packages for integrating Unity Catalog with various AI frameworks
- **Client Libraries**: SDKs for Java and Python
- **Web UI**: React-based user interface
- **CLI Tools**: Command-line interface for catalog operations

## CRITICAL: Plan First Guardrail

**Before implementing ANY code changes, you MUST:**

1. **Identify and list specific files to be modified**:
   - List exact file paths that will be changed
   - Specify whether each file will be created, modified, or deleted
   - Note line numbers or sections for complex changes

2. **Describe the specific changes**:
   - What code/content will be added
   - What code/content will be removed or modified
   - Any dependencies or related files affected

3. **Present the plan to the user**:
   - Provide a clear, numbered list of changes
   - Wait for user confirmation before proceeding
   - If the task is unclear, ask clarifying questions first

4. **Avoid runaway implementations**:
   - Never make changes without a clear plan
   - Stop immediately if encountering unexpected complexity
   - Don't enter loops of corrections without reassessing the approach
   - If errors persist after 2 attempts, stop and ask for guidance

5. **Example planning format**:
   ```
   I'll make the following changes:
   1. Modify `server/src/main/java/.../TableService.java`:
      - Add method `validateTableName()` at line ~150
   2. Update `api/all.yaml`:
      - Add optional parameter `validate` to POST /tables endpoint
   3. Create test file `server/src/test/.../TableServiceTest.java`:
      - Add unit tests for new validation logic

   Shall I proceed with these changes?
   ```

This planning step prevents:
- Runaway implementations that make unnecessary changes
- "Death loops" of repeated corrections
- Modifications to the wrong files
- Breaking existing functionality unexpectedly

## Core Build Commands

### Main Build System (sbt)
- `build/sbt clean compile` - Compile all code without running tests
- `build/sbt clean package` - Build JARs for all modules
- `build/sbt clean package publishLocal` - Build and publish to local Maven repository
- `build/sbt -J-Xmx2G clean test` - Run all tests with increased memory
- `build/sbt jacoco` - Run tests with coverage
- `build/sbt javafmtAll` - Format Java code
- `build/sbt javafmtCheckAll` - Check Java code formatting
- `build/sbt generate` - Regenerate OpenAPI models from `api/all.yaml` and `api/control.yaml`
- `build/sbt createTarball` - Create deployment tarball

### Server Operations
- `bin/start-uc-server` - Start Unity Catalog server (auto-builds if needed)
- Server runs on port 8080 by default
- Configuration files in `etc/conf/`

### Python AI Components
All AI integration packages use Python with these common commands:
- `pip install -e .` - Install package in development mode
- `pytest` - Run tests
- `ruff check` - Lint Python code
- `ruff format` - Format Python code

### UI Development
- `cd ui && yarn install` - Install dependencies
- `cd ui && yarn start` - Start development server on port 3000
- `cd ui && yarn build` - Build for production
- `cd ui && yarn test:format` - Check code formatting

### Docker Operations
- `docker compose up` - Start server and UI in containers
- Uses `compose.yaml` configuration

## Architecture

### Server Architecture (Java)
Located in `server/src/main/java/io/unitycatalog/server/`:

- **UnityCatalogServer.java** - Main server entry point using Armeria framework
- **service/** - Business logic services for catalogs, schemas, tables, functions, models, volumes
- **persist/** - Data access layer with Hibernate ORM and repository pattern
- **auth/** - Authentication and authorization using Casbin
- **security/** - JWT and security context management
- **utils/** - Common utilities and configuration management

### AI Core Library (Python)
Located in `ai/core/src/unitycatalog/ai/core/`:

- **client.py** - Main Unity Catalog function client for OSS
- **databricks.py** - Databricks-managed Unity Catalog client
- **executor/** - Function execution engines (local and sandbox modes)
- **utils/** - Utility modules for type handling, validation, and execution

### AI Integrations (Python)
Each integration in `ai/integrations/` follows the same pattern:
- **toolkit.py** - Main toolkit class that wraps Unity Catalog functions as AI tools
- **utils.py** - Integration-specific utilities (where applicable)
- Supported frameworks: Anthropic, OpenAI, LangChain, LlamaIndex, Gemini, LiteLM, AutoGen, CrewAI

### Client Libraries
- **Java Client**: Generated from OpenAPI specs in `target/clients/java/`
- **Python Client**: Generated from OpenAPI specs in `clients/python/`
- Both auto-generated from `api/all.yaml` using OpenAPI Generator

### OpenAPI Specifications
- `api/all.yaml` - Complete Unity Catalog API specification
- `api/control.yaml` - Control plane API for internal operations
- Models auto-generated into server and client code
- API documentation in `api/Apis/` and `api/Models/` directories
- Base API endpoint: `http://localhost:8080/api/2.1/unity-catalog`

## Development Patterns

### Java Code Standards
- Uses Google Java format (enforced by `javafmtAll`)
- Checkstyle configuration in `dev/checkstyle-config.xml`
- Java 17 for server components, Java 11 for client and Spark connector
- Hibernate ORM for data persistence
- Repository pattern for data access
- Service layer pattern for business logic

### Python Code Standards
- Ruff for linting and formatting (configured in `pyproject.toml`)
- Type hints required for all public APIs
- Google-style docstrings
- Pytest for testing with async support
- Pydantic for data validation

### Testing Patterns
- **Java**: JUnit 5 with Mockito for mocking
- **Python**: pytest with pytest-asyncio for async tests
- **Integration**: Separate integration test module
- Test data in `etc/data/` directory

### Code Generation
Many components are auto-generated:
- OpenAPI models and APIs regenerated via `build/sbt generate`
- Client SDKs generated from API specs
- Version information injected at build time

## Key Configuration Files

- `build.sbt` - Main build configuration with all module definitions
- `pyproject.toml` - Python package configuration for AI components
- `etc/conf/server.properties` - Server runtime configuration
- `etc/conf/hibernate.properties` - Database configuration
- `compose.yaml` - Docker Compose setup

## Function Execution Modes

Unity Catalog AI supports two execution modes:
- **Sandbox Mode** (default): Isolated process execution with CPU/memory limits
- **Local Mode**: In-process execution with full system access

Environment variables for sandbox tuning:
- `EXECUTOR_MAX_CPU_TIME_LIMIT` (default: 10s)
- `EXECUTOR_MAX_MEMORY_LIMIT` (default: 100MB)
- `EXECUTOR_TIMEOUT` (default: 20s)

## Common Development Tasks

### Adding New AI Integration
1. Create new directory in `ai/integrations/`
2. Follow existing pattern with `toolkit.py`, `pyproject.toml`, tests
3. Implement toolkit class inheriting from base patterns
4. Add example notebooks

### Modifying APIs
1. Update OpenAPI spec in `api/all.yaml` or `api/control.yaml`
2. Run `build/sbt generate` to regenerate models
3. Update service implementations in `server/src/main/java/io/unitycatalog/server/service/`
4. Update tests

### Working with Functions
- Functions stored in catalog.schema.function_name format
- Support both SQL and Python function bodies
- Execution can be local or serverless (Databricks)
- Function metadata includes parameter schemas and dependencies

### Unity Catalog Namespace Hierarchy
- **Level 1: Catalogs** - Top-level organization containers
- **Level 2: Schemas** - Organize tables, volumes, and functions (also called databases)
- **Level 3: Assets** - Tables, Volumes, Functions, RegisteredModels, ModelVersions
- Full asset reference: `catalog_name.schema_name.asset_name`

## Authorization System (JCasbin)

Unity Catalog uses JCasbin for access control with a Role-Based Access Control (RBAC) model that supports resource hierarchies.

### JCasbin Model Configuration
Located in `server/src/main/resources/jcasbin_auth_model.conf`:
- **Request Definition**: `(subject, object, action)` - principal, resource, privilege
- **Policy Definition**: Stores authorization rules
- **Role Definitions**:
  - `g = _, _` - Principal role inheritance
  - `g2 = _, _` - Resource hierarchy (parent-child relationships)
- **Policy Effect**: Allow if any matching rule permits
- **Matcher**: Evaluates policies considering both role and resource hierarchies

### Authorization Implementation
- **Authorizer**: `JCasbinAuthorizer` implements `UnityCatalogAuthorizer` interface
- **Storage**: Policies stored in database via JDBC adapter (uses Hibernate connection)
- **Auto-save**: Changes persist immediately to database

### Supported Privileges
Defined in `Privileges` enum:
- **Ownership**: `OWNER`
- **Catalog**: `CREATE_CATALOG`, `USE_CATALOG`
- **Schema**: `CREATE_SCHEMA`, `USE_SCHEMA`
- **Table**: `CREATE_TABLE`, `SELECT`, `MODIFY`
- **Function**: `CREATE_FUNCTION`, `EXECUTE`
- **Volume**: `CREATE_VOLUME`, `READ_VOLUME`
- **Model**: `CREATE_MODEL`

### Authorization Operations
- **Grant/Revoke**: Add or remove specific permissions
- **Hierarchy Management**: Parent resources inherit permissions to children
- **Bulk Operations**: Clear all permissions for a principal or resource
- **Authorization Checks**:
  - Single permission: `authorize(principal, resource, privilege)`
  - Any of multiple: `authorizeAny(principal, resource, privileges...)`
  - All of multiple: `authorizeAll(principal, resource, privileges...)`

### Enabling Authorization
- Controlled by `server.properties` configuration
- When enabled, initialized in `UnityCatalogServer` with database connection
- Falls back to `AllowingAuthorizer` (permits all) when disabled

## Cloud Storage Credential Vending

Unity Catalog provides temporary, scoped credentials for secure access to cloud storage (S3, Azure ADLS, GCS) without exposing long-lived credentials to clients.

### Architecture Overview
- **CloudCredentialVendor**: Unified interface routing to cloud-specific vendors
- **Credential Context**: Contains storage location, privileges (SELECT/UPDATE), and paths
- **Temporary Credentials**: Short-lived tokens with minimal required permissions
- Supports multi-cloud: AWS S3, Azure ADLS, Google Cloud Storage

### AWS S3 Credential Vending

#### Configuration in `server.properties`
```properties
# Multiple S3 configurations supported (increment index for each)
s3.bucketPath.0=s3://my-bucket
s3.region.0=us-west-2
s3.awsRoleArn.0=arn:aws:iam::123456789012:role/unity-catalog-role
# Optional: Static credentials (uses DefaultCredentialsProvider if blank)
s3.accessKey.0=
s3.secretKey.0=
# Test only: Static session token (bypasses STS AssumeRole)
s3.sessionToken.0=
```

#### STS AssumeRole Flow
1. **AwsCredentialVendor** (`service/credential/aws/AwsCredentialVendor.java`):
   - Retrieves S3 configuration for requested storage location
   - Creates STS client with server credentials (static or default provider)
   - Calls `AssumeRole` with generated IAM policy
   - Returns temporary credentials (1-hour duration)

2. **AwsPolicyGenerator** (`service/credential/aws/AwsPolicyGenerator.java`):
   - Generates least-privilege IAM policies dynamically
   - Groups locations by S3 bucket
   - Creates bucket ListBucket permissions with prefix conditions
   - Adds object-level permissions based on requested privileges

#### Privilege to AWS Permission Mapping
- **SELECT** privilege → `s3:GetO*` actions (read-only)
- **UPDATE** privilege → `s3:GetO*`, `s3:PutO*`, `s3:DeleteO*`, `s3:*Multipart*` (read/write)

#### Generated IAM Policy Structure
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["s3:GetO*", "s3:PutO*", ...],
      "Resource": ["arn:aws:s3:::bucket/path/*"]
    },
    {
      "Effect": "Allow",
      "Action": ["s3:ListBucket"],
      "Resource": ["arn:aws:s3:::bucket"],
      "Condition": {
        "StringLike": {
          "s3:prefix": ["path/*"]
        }
      }
    }
  ]
}
```

### Credential Vending Services
- **TemporaryTableCredentialsService**: Vends credentials for table storage locations
- **TemporaryVolumeCredentialsService**: Vends credentials for volume storage
- **TemporaryModelVersionCredentialsService**: Vends credentials for ML model storage
- **TemporaryPathCredentialsService**: General path-based credential vending

### Security Model
- Credentials scoped to specific storage paths and operations
- Temporary credentials expire after 1 hour (configurable)
- Authorization checks before vending (READ requires SELECT, WRITE requires SELECT+MODIFY or OWNER)
- Supports both IAM role assumption and static credentials (for testing)

## Spark Connector Integration

### Overview
The Unity Catalog Spark connector (`io.unitycatalog.spark.UCSingleCatalog`) enables Spark to work with Unity Catalog tables while transparently handling cloud storage authentication.

### Architecture
- **UCSingleCatalog**: Main catalog implementation extending Spark's `TableCatalog` and `SupportsNamespaces`
- **UCProxy**: Internal proxy for Unity Catalog API communication
- **DeltaCatalog Integration**: Optionally wraps Delta Lake catalog for Delta table support
- **Credential Injection**: Automatically injects cloud credentials into Hadoop configuration

### Spark Configuration
```properties
spark.sql.catalog.<catalog_name>=io.unitycatalog.spark.UCSingleCatalog
spark.sql.catalog.<catalog_name>.uri=http://localhost:8080
spark.sql.catalog.<catalog_name>.token=<optional_auth_token>
```

### Credential Vending Flow

#### 1. Table Load Process
When Spark loads a table (`loadTable`):
1. Fetches table metadata from Unity Catalog API
2. Requests temporary credentials via `TemporaryCredentialsApi`
3. Initially requests READ_WRITE credentials (falls back to READ if denied)
4. Generates cloud-specific Hadoop configuration properties
5. Injects credentials into table's storage properties

#### 2. Table Creation Process
When creating tables with external locations (`createTable`):
1. Validates storage location via `generateTemporaryPathCredentials`
2. Requests PATH_CREATE_TABLE operation credentials
3. Injects credentials into table properties for immediate access
4. Credentials stored with and without `option.` prefix (Delta requirement)

### S3 Credential Mapping
The connector maps AWS temporary credentials to Hadoop S3A properties:

```scala
// From UCSingleCatalog.generateCredentialProps
Map(
  "fs.s3a.access.key" -> awsCredentials.getAccessKeyId,
  "fs.s3a.secret.key" -> awsCredentials.getSecretAccessKey,
  "fs.s3a.session.token" -> awsCredentials.getSessionToken,
  "fs.s3a.path.style.access" -> "true",
  "fs.s3a.impl.disable.cache" -> "true"  // Prevents credential caching issues
)
```

### Azure ADLS Credential Mapping
```scala
Map(
  "fs.azure.account.auth.type.property.name" -> "SAS",
  "fs.azure.account.is.hns.enabled" -> "true",
  "fs.azure.sas.token.provider.type" -> classOf[AbfsVendedTokenProvider].getName,
  AbfsVendedTokenProvider.ACCESS_TOKEN_KEY -> azCredentials.getSasToken
)
```

### GCS Credential Mapping
```scala
Map(
  "fs.gs.auth.type" -> "ACCESS_TOKEN_PROVIDER",
  "fs.gs.auth.access.token.provider" -> classOf[GcsVendedTokenProvider].getName,
  GcsVendedTokenProvider.ACCESS_TOKEN_KEY -> gcsCredentials.getOauthToken,
  GcsVendedTokenProvider.ACCESS_TOKEN_EXPIRATION_KEY -> expirationTime
)
```

### Custom Token Providers
- **AbfsVendedTokenProvider**: Provides Azure SAS tokens to Hadoop Azure filesystem
- **GcsVendedTokenProvider**: Provides GCS OAuth tokens to Google Cloud Storage connector

### Testing Infrastructure
- **CredentialTestFileSystem**: Base class for testing credential injection
- **S3CredentialTestFileSystem**: Validates S3 credentials are properly configured
- Tests verify credentials are correctly passed to Hadoop filesystem layer

### Key Design Decisions
1. **Automatic Credential Injection**: Credentials transparently added to table storage properties
2. **Filesystem Cache Disabled**: `fs.*.impl.disable.cache=true` prevents stale credentials
3. **Fallback Logic**: Gracefully handles permission denials by requesting lesser privileges
4. **Multi-Cloud Support**: Unified interface for S3, Azure, and GCS credentials

## API Development Guardrails

These guardrails ensure backward compatibility while adding new functionality to Unity Catalog.

### OpenAPI and Code Generation
**NEVER modify auto-generated code directly**
- All API changes must start in `api/all.yaml` or `api/control.yaml`
- Run `build/sbt generate` after OpenAPI modifications
- Generated code locations:
  - Server models: Auto-generated from OpenAPI specs
  - Client SDKs: `target/clients/java/` and `clients/python/`
- Mark experimental APIs with clear warnings in descriptions

### API Compatibility Rules
**Backward compatibility is mandatory**
1. **Never break existing endpoints**:
   - Don't change HTTP methods (GET, POST, etc.)
   - Don't modify URL paths
   - Don't remove or rename required parameters
   - Don't change response structures

2. **Safe additions only**:
   - Add optional parameters with defaults
   - Add new endpoints rather than modifying existing ones
   - Add fields to responses (clients should ignore unknown fields)
   - Use API versioning for breaking changes (current: `/api/2.1/unity-catalog`)

3. **Deprecation process**:
   - Mark deprecated features in OpenAPI spec
   - Provide migration timeline (minimum 2 releases)
   - Log deprecation warnings in server
   - Document replacement APIs

### Service Layer Extensions
**Extend, don't modify**
- Create new service classes for new functionality
- Use composition over inheritance
- Extend existing interfaces with default methods
- Keep service methods focused and single-purpose

Example pattern:
```java
// DON'T modify existing service
public class TableService { ... }

// DO create extension service
public class ExtendedTableService {
    private final TableService tableService;
    // New functionality here
}
```

### Database Schema Evolution
**Only additive changes allowed**
1. **Column rules**:
   - New columns must be nullable OR have defaults
   - Never remove or rename columns
   - Use `@Column(name = "old_name")` if renaming internally

2. **Table rules**:
   - Create new tables for new features
   - Never drop existing tables
   - Use Hibernate migrations for changes

3. **Enum extensions**:
   - Only add new values to enums (like Privileges)
   - Never remove or reorder existing values
   - Handle unknown enum values gracefully

### Configuration Compatibility
**Properties are contracts**
```properties
# DON'T remove or rename
old.property=value

# DO add with defaults
old.property=value
new.property=default_value

# DO support aliases during transition
new.name=value  # Renamed from old.property
```

### Spark Connector Compatibility
**Critical integration point**
- Test with multiple Spark versions (3.3, 3.4, 3.5)
- Preserve `UCSingleCatalog` interfaces
- Keep credential property mappings stable
- Maintain Hadoop filesystem configurations
- Test Delta Lake integration compatibility

### Authorization Model Stability
**JCasbin policies are persistent**
- Don't change privilege semantics
- Add new privileges, don't modify existing ones
- Preserve resource hierarchy relationships
- Keep matcher expressions backward compatible

### Testing Requirements
Before submitting changes:
1. **Unit tests**: Cover new functionality
2. **Integration tests**: Verify with existing clients
3. **Compatibility tests**: Run against previous versions
4. **Spark tests**: Verify connector functionality
5. **API contract tests**: Ensure response compatibility

### Common Pitfalls to Avoid
❌ **DON'T**:
- Modify generated code manually
- Change existing API contracts
- Remove configuration properties
- Alter privilege meanings
- Break Spark connector compatibility

✅ **DO**:
- Add optional parameters
- Create new endpoints
- Extend with new services
- Add nullable columns
- Provide migration paths
- Test thoroughly with existing clients

### Review Checklist
Before making changes:
- [ ] Is this change backward compatible?
- [ ] Have I modified OpenAPI specs, not generated code?
- [ ] Are new parameters optional with defaults?
- [ ] Do existing clients still work?
- [ ] Have I tested with Spark connector?
- [ ] Are database changes additive only?
- [ ] Is configuration compatibility maintained?

### Getting Help
- Check existing patterns in codebase
- Review recent PRs for examples
- Ask in Slack before major changes
- Create GitHub issue for design discussion