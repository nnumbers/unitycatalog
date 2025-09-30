# Feature Plan: Table Creation Path Credentials for Non-Admin Users

## Problem Statement
Non-admin users with CREATE TABLE privileges cannot create S3-backed tables because the TemporaryPathCredentialsService requires METASTORE OWNER privilege, effectively breaking the primary use case.

## Proposed Solution: Delegated Path Credentials for Table Creation

### Core Design Principle
Leverage the existing privilege system to delegate path credential vending to users who have appropriate table creation privileges, without granting them blanket METASTORE OWNER access.

### Key Components

#### 1. New API Endpoint: `/temporary-table-creation-credentials`
- **Purpose**: Vend credentials specifically for table creation locations
- **Authorization**: Requires CREATE TABLE privilege on schema + USE privileges on parent resources
- **Scope**: Limited to paths within configured storage boundaries

#### 2. Storage Location Validation
- Validate requested paths against allowed storage prefixes configured per schema/catalog
- Prevent arbitrary path access while enabling legitimate table creation
- Use existing server.properties S3/Azure/GCS configurations as boundaries

#### 3. Authorization Model (No Breaking Changes)
```java
@AuthorizeExpression("""
  #authorize(#principal, #catalog, USE_CATALOG) &&
  #authorize(#principal, #schema, USE_SCHEMA) &&
  #authorize(#principal, #schema, CREATE_TABLE)
  """)
```

### Implementation Steps

#### Phase 1: Add New Credential Endpoint
1. Create `TemporaryTableCreationCredentialsService.java`
2. Add `/temporary-table-creation-credentials` to OpenAPI spec
3. Map PATH_CREATE_TABLE operation to SELECT+UPDATE privileges
4. Validate storage location against schema's allowed prefixes

#### Phase 2: Update Spark Connector
1. Modify `UCSingleCatalog.scala` to check for CREATE TABLE privilege first
2. Try new endpoint before falling back to path credentials endpoint
3. Maintain backward compatibility with existing OWNER-based flow

#### Phase 3: Schema Storage Configuration
1. Add optional `allowed_storage_prefixes` property to schema metadata
2. Default to catalog-level storage configuration if not specified
3. Validate all table locations against these prefixes

### Security Considerations
- Credentials remain temporary (1-hour expiration)
- Scoped to specific storage paths, not entire buckets
- Audit logging for all credential vending operations
- No elevation of privileges beyond what CREATE TABLE implies

### Backward Compatibility
- ✅ Existing OWNER-based flow remains unchanged
- ✅ New endpoint is additive, not modifying existing ones
- ✅ Falls back gracefully if new endpoint returns 403
- ✅ No changes to existing privilege definitions

### Benefits
- Non-admin users can create S3-backed tables with just CREATE TABLE privilege
- Maintains security through path validation and temporary credentials
- Aligns authorization with actual capabilities
- Simple implementation leveraging existing patterns