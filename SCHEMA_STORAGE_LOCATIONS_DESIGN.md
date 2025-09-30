# Schema Storage Locations and Managed Tables Design

## Overview

This design adds storage location management at the catalog and schema levels to:
1. Enable secure credential vending for external table creation by non-admin users
2. Lay groundwork for managed table support
3. Enforce storage boundaries in multi-tenant environments

## Current State

**What exists:**
- `TableType` enum with `MANAGED` and `EXTERNAL` values
- External tables require user-provided `storage_location`
- Managed table creation is explicitly blocked ([TableRepository.java:150-153](server/src/main/java/io/unitycatalog/server/persist/TableRepository.java#L150-L153))
- Delete logic for managed tables exists (removes storage directory)

**What's missing:**
- Catalog-level storage root configuration
- Schema-level storage locations
- Storage path validation for external tables
- Automatic path generation for managed tables
- Credential vending for CREATE_TABLE operations

## Goals

1. **External Tables (Immediate)**: Users with CREATE_TABLE privilege can create tables within schema-defined storage boundaries
2. **Managed Tables (Future-Ready)**: Infrastructure supports automatic path generation under catalog/schema hierarchy
3. **Multi-Tenant Security**: Prevent users from vending credentials for arbitrary S3 paths
4. **Databricks Compatibility**: Follow commercial Unity Catalog conventions

## Design

### 1. Storage Location Hierarchy

```
Catalog
├── storage_root: s3://my-catalog-bucket/
└── Schema
    ├── storage_location: s3://my-catalog-bucket/my-schema/
    └── Tables
        ├── Managed Table: s3://my-catalog-bucket/my-schema/table1/ (auto-generated)
        └── External Table: s3://my-catalog-bucket/my-schema/external/table2/ (user-provided, validated)
```

**Key Principles:**
- **Catalog** defines the root bucket for all managed storage
- **Schema** defines a path prefix under the catalog bucket
- **Managed tables** get auto-generated paths: `{schema.storage_location}/{table_name}/`
- **External tables** must specify location within `{schema.storage_location}` boundary (can be in same or different bucket)

### 2. Database Schema Changes

#### Catalog Storage Root

```sql
ALTER TABLE uc_catalogs ADD COLUMN storage_root VARCHAR(255);
```

**Properties:**
- Optional field (nullable)
- Must be a valid S3 URI (e.g., `s3://bucket/` or `s3://bucket/prefix/`)
- Required for creating managed tables in this catalog
- Can point to any S3 bucket configured in server.properties

#### Schema Storage Location

```sql
ALTER TABLE uc_schemas ADD COLUMN storage_location VARCHAR(1024);
```

**Properties:**
- Optional field (nullable)
- Must be under catalog's `storage_root` (if catalog has one)
- Used as base path for managed tables: `{storage_location}/{table_name}/`
- Used as boundary for external table validation

### 3. API Changes

#### Update OpenAPI Spec (api/all.yaml)

**CatalogInfo model:**
```yaml
CatalogInfo:
  properties:
    # ... existing fields ...
    storage_root:
      description: |
        Optional S3 URI for managed table storage root.
        Example: s3://my-catalog-bucket/ or s3://my-catalog-bucket/prod/
        Required for creating managed tables in this catalog.
      type: string
```

**SchemaInfo model:**
```yaml
SchemaInfo:
  properties:
    # ... existing fields ...
    storage_location:
      description: |
        Optional S3 URI path for schema storage.
        - For managed tables: base path for auto-generated table locations
        - For external tables: allowed location boundary for validation
        Example: s3://my-catalog-bucket/my-schema/
        Must be under catalog's storage_root if catalog defines one.
      type: string
```

**CreateTable model:**
```yaml
CreateTable:
  properties:
    # ... existing fields ...
    storage_location:
      description: |
        Storage location for the table.
        - MANAGED tables: Optional. If not provided, auto-generated as {schema.storage_location}/{table_name}/
        - EXTERNAL tables: Required. Must be within schema's storage_location boundary.
      type: string
```

#### New API Endpoint

```yaml
/temporary-table-creation-credentials:
  post:
    summary: Generate temporary credentials for table creation
    description: |
      Vends temporary S3 credentials for creating a table.
      - If storage_location provided: validates against schema boundary and vends credentials
      - If no storage_location: generates managed table path and vends credentials

      Authorization: Requires CREATE_TABLE + USE_SCHEMA + USE_CATALOG privileges
    requestBody:
      required: true
      content:
        application/json:
          schema:
            type: object
            required:
              - catalog_name
              - schema_name
              - table_name
            properties:
              catalog_name:
                type: string
              schema_name:
                type: string
              table_name:
                type: string
              storage_location:
                type: string
                description: Optional. For external tables, must be within schema boundary.
    responses:
      200:
        description: Temporary credentials for the storage location
        content:
          application/json:
            schema:
              type: object
              properties:
                storage_location:
                  type: string
                  description: The validated/generated storage location to use
                credentials:
                  $ref: '#/components/schemas/AwsCredentials'
      403:
        description: |
          - User lacks required privileges
          - Storage location outside schema boundary
          - Schema not configured for external tables
```

### 4. Implementation Details

#### Phase 1: Database and API Models (Required)

**Files to modify:**
1. `server/src/main/java/io/unitycatalog/server/persist/dao/CatalogInfoDAO.java`
   - Add `@Column(name = "storage_root") private String storageRoot;`

2. `server/src/main/java/io/unitycatalog/server/persist/dao/SchemaInfoDAO.java`
   - Add `@Column(name = "storage_location") private String storageLocation;`

3. `api/all.yaml`
   - Add `storage_root` to CatalogInfo
   - Add `storage_location` to SchemaInfo
   - Update CreateTable.storage_location description
   - Add `/temporary-table-creation-credentials` endpoint

4. Run `build/sbt generate` to regenerate models

5. Database migration (Hibernate will auto-create columns if using auto-ddl, otherwise manual migration needed)

#### Phase 2: Storage Location Validation (Required)

**New utility class:** `server/src/main/java/io/unitycatalog/server/utils/StorageLocationValidator.java`

```java
public class StorageLocationValidator {

  /**
   * Validates that a storage location is within an allowed boundary.
   *
   * @param location The location to validate (e.g., s3://bucket/schema/external/table/)
   * @param boundary The allowed boundary (e.g., s3://bucket/schema/)
   * @return true if location is under boundary
   */
  public static boolean isWithinBoundary(String location, String boundary) {
    if (boundary == null || boundary.isEmpty()) {
      return false; // No boundary configured = deny by default
    }

    URI locationUri = URI.create(location);
    URI boundaryUri = URI.create(boundary);

    // Must be same scheme (s3://)
    if (!locationUri.getScheme().equals(boundaryUri.getScheme())) {
      return false;
    }

    // Must be same bucket (for s3://)
    if (!locationUri.getHost().equals(boundaryUri.getHost())) {
      return false;
    }

    // Location path must start with boundary path
    String locationPath = locationUri.getPath();
    String boundaryPath = boundaryUri.getPath();

    return locationPath.startsWith(boundaryPath);
  }

  /**
   * Generates managed table path.
   *
   * @param schemaStorageLocation Base path (e.g., s3://bucket/schema/)
   * @param tableName Table name
   * @return Generated path (e.g., s3://bucket/schema/table_name/)
   */
  public static String generateManagedTablePath(String schemaStorageLocation, String tableName) {
    if (schemaStorageLocation == null || schemaStorageLocation.isEmpty()) {
      throw new BaseException(
          ErrorCode.INVALID_ARGUMENT,
          "Schema storage_location must be configured for managed tables");
    }

    // Ensure schema location ends with /
    String baseLocation = schemaStorageLocation.endsWith("/")
        ? schemaStorageLocation
        : schemaStorageLocation + "/";

    return baseLocation + tableName + "/";
  }

  /**
   * Validates catalog storage root format.
   */
  public static void validateStorageRoot(String storageRoot) {
    if (storageRoot == null) return; // Optional field

    URI uri = URI.create(storageRoot);
    if (!"s3".equals(uri.getScheme()) && !"s3a".equals(uri.getScheme())) {
      throw new BaseException(
          ErrorCode.INVALID_ARGUMENT,
          "Storage root must be an s3:// URI");
    }
  }

  /**
   * Validates schema storage location is under catalog root.
   */
  public static void validateSchemaLocation(String catalogRoot, String schemaLocation) {
    if (schemaLocation == null) return; // Optional field
    if (catalogRoot == null) return; // No catalog root to validate against

    if (!isWithinBoundary(schemaLocation, catalogRoot)) {
      throw new BaseException(
          ErrorCode.INVALID_ARGUMENT,
          "Schema storage_location must be under catalog storage_root");
    }
  }
}
```

#### Phase 3A: Managed Table Creation (Optional - Can be Phase 4)

**Modify:** `server/src/main/java/io/unitycatalog/server/persist/TableRepository.java`

```java
public TableInfo createTable(CreateTable createTable) {
  // ... existing validation ...

  return TransactionManager.executeWithTransaction(
      sessionFactory,
      session -> {
        String catalogName = tableInfo.getCatalogName();
        String schemaName = tableInfo.getSchemaName();
        UUID schemaId = getSchemaId(session, catalogName, schemaName);

        // Get schema info for storage location
        SchemaInfoDAO schemaDAO = session.get(SchemaInfoDAO.class, schemaId);
        String schemaStorageLocation = schemaDAO.getStorageLocation();

        // Handle managed vs external tables
        if (TableType.MANAGED.equals(tableInfo.getTableType())) {
          // REMOVE the exception that blocks managed tables

          // Auto-generate storage location
          String generatedLocation = StorageLocationValidator.generateManagedTablePath(
              schemaStorageLocation,
              tableInfo.getName());
          tableInfo.setStorageLocation(generatedLocation);

          // Create directory (if needed by storage backend)
          try {
            fileOperations.createDirectory(generatedLocation);
          } catch (Exception e) {
            throw new BaseException(
                ErrorCode.INTERNAL_ERROR,
                "Failed to create managed table directory: " + e.getMessage());
          }
        } else {
          // EXTERNAL table
          if (tableInfo.getStorageLocation() == null) {
            throw new BaseException(
                ErrorCode.INVALID_ARGUMENT,
                "Storage location is required for external table");
          }

          // Validate external location is within schema boundary
          if (!StorageLocationValidator.isWithinBoundary(
                  tableInfo.getStorageLocation(),
                  schemaStorageLocation)) {
            throw new BaseException(
                ErrorCode.PERMISSION_DENIED,
                "External table location must be within schema storage boundary: " +
                schemaStorageLocation);
          }
        }

        // ... rest of existing table creation logic ...
      },
      "Error creating table: " + fullName,
      /* readOnly = */ false);
}
```

#### Phase 3B: Credential Vending Service (Required)

**New service:** `server/src/main/java/io/unitycatalog/server/service/TemporaryTableCreationCredentialsService.java`

```java
@Tag(name = "TemporaryTableCredentials")
@Path("/temporary-table-creation-credentials")
@RolesAllowed(Roles.USER)
public class TemporaryTableCreationCredentialsService extends BaseService {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(TemporaryTableCreationCredentialsService.class);

  private final CloudCredentialVendor credentialVendor;
  private final UnityCatalogAuthorizer authorizer;

  @Inject
  public TemporaryTableCreationCredentialsService(
      UnityCatalogAuthorizer authorizer,
      CloudCredentialVendor credentialVendor) {
    this.authorizer = authorizer;
    this.credentialVendor = credentialVendor;
  }

  @Post("")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public HttpResponse generateTableCreationCredentials(
      GenerateTemporaryTableCreationCredential request) {

    String catalogName = request.getCatalogName();
    String schemaName = request.getSchemaName();
    String tableName = request.getTableName();
    String requestedLocation = request.getStorageLocation(); // May be null

    // Authorization check: CREATE_TABLE + USE_SCHEMA + USE_CATALOG
    String principal = IdentityUtils.findPrincipalEmailAddress();
    String schemaFullName = catalogName + "." + schemaName;

    authorizer.authorize(principal, schemaFullName, Privileges.CREATE_TABLE);
    authorizer.authorize(principal, schemaFullName, Privileges.USE_SCHEMA);
    authorizer.authorize(principal, catalogName, Privileges.USE_CATALOG);

    // Get schema storage location
    SchemaInfo schema = getSchemaRepository().getSchema(catalogName, schemaName);
    String schemaStorageLocation = schema.getStorageLocation();

    if (schemaStorageLocation == null || schemaStorageLocation.isEmpty()) {
      throw new BaseException(
          ErrorCode.INVALID_ARGUMENT,
          "Schema " + schemaFullName + " does not have storage_location configured. " +
          "Cannot create tables in this schema.");
    }

    // Determine final storage location
    String storageLocation;
    if (requestedLocation != null && !requestedLocation.isEmpty()) {
      // External table: validate location is within schema boundary
      if (!StorageLocationValidator.isWithinBoundary(requestedLocation, schemaStorageLocation)) {
        throw new BaseException(
            ErrorCode.PERMISSION_DENIED,
            "Requested storage location must be within schema boundary: " + schemaStorageLocation);
      }
      storageLocation = requestedLocation;
    } else {
      // Managed table: generate location
      storageLocation = StorageLocationValidator.generateManagedTablePath(
          schemaStorageLocation,
          tableName);
    }

    // Vend credentials with WRITE privilege (CREATE + READ + WRITE)
    Set<Privileges> privileges = Set.of(Privileges.UPDATE); // UPDATE = read + write
    CredentialContext context = new CredentialContext(
        storageLocation,
        privileges,
        Collections.singletonList(storageLocation));

    CloudCredential credentials = credentialVendor.vendCredential(context);

    // Return response
    GenerateTemporaryTableCreationCredentialResponse response =
        new GenerateTemporaryTableCreationCredentialResponse()
            .storageLocation(storageLocation)
            .awsCredentials((AwsCredentials) credentials);

    return HttpResponse.ofJson(response);
  }
}
```

#### Phase 4: Catalog/Schema Service Updates

**Modify:** `server/src/main/java/io/unitycatalog/server/service/CatalogService.java`

```java
public CatalogInfo createCatalog(CreateCatalog createCatalog) {
  // ... existing code ...

  // Validate storage root if provided
  if (createCatalog.getStorageRoot() != null) {
    StorageLocationValidator.validateStorageRoot(createCatalog.getStorageRoot());
  }

  // ... rest of creation logic ...
}

public CatalogInfo updateCatalog(UpdateCatalog updateCatalog) {
  // ... existing code ...

  // Validate storage root if being updated
  if (updateCatalog.getStorageRoot() != null) {
    StorageLocationValidator.validateStorageRoot(updateCatalog.getStorageRoot());
  }

  // ... rest of update logic ...
}
```

**Modify:** `server/src/main/java/io/unitycatalog/server/service/SchemaService.java`

```java
public SchemaInfo createSchema(CreateSchema createSchema) {
  // ... existing code ...

  // If storage_location provided, validate against catalog storage_root
  if (createSchema.getStorageLocation() != null) {
    CatalogInfo catalog = getCatalogRepository().getCatalog(createSchema.getCatalogName());
    StorageLocationValidator.validateSchemaLocation(
        catalog.getStorageRoot(),
        createSchema.getStorageLocation());
  }

  // ... rest of creation logic ...
}

public SchemaInfo updateSchema(UpdateSchema updateSchema) {
  // ... existing code ...

  // If storage_location being updated, validate
  if (updateSchema.getStorageLocation() != null) {
    SchemaInfo existingSchema = getSchemaRepository().getSchema(/* ... */);
    CatalogInfo catalog = getCatalogRepository().getCatalogById(/* ... */);
    StorageLocationValidator.validateSchemaLocation(
        catalog.getStorageRoot(),
        updateSchema.getStorageLocation());
  }

  // ... rest of update logic ...
}
```

#### Phase 5: Register New Service in Server

**Modify:** `server/src/main/java/io/unitycatalog/server/UnityCatalogServer.java`

```java
// Add to service registration
serverBuilder.annotatedService(
    "/api/2.1/unity-catalog",
    new TemporaryTableCreationCredentialsService(authorizer, cloudCredentialVendor));
```

### 5. Configuration Examples

#### Server Configuration (etc/conf/server.properties)

```properties
# S3 bucket configurations (existing)
s3.bucketPath.0=s3://my-catalog-bucket
s3.region.0=us-west-2
s3.awsRoleArn.0=arn:aws:iam::123456789012:role/unity-catalog-role

s3.bucketPath.1=s3://external-data-bucket
s3.region.1=us-west-2
s3.awsRoleArn.1=arn:aws:iam::123456789012:role/unity-catalog-role
```

#### Catalog Setup (CLI)

```bash
# Create catalog with storage root
bin/uc catalog create \
  --name production \
  --comment "Production catalog" \
  --storage_root s3://my-catalog-bucket/prod/

# Create schema with storage location under catalog root
bin/uc schema create \
  --catalog production \
  --name sales \
  --storage_location s3://my-catalog-bucket/prod/sales/
```

#### Spark Usage

```scala
// Configure Spark to use Unity Catalog
spark.conf.set("spark.sql.catalog.unity", "io.unitycatalog.spark.UCSingleCatalog")
spark.conf.set("spark.sql.catalog.unity.uri", "http://localhost:8080")
spark.conf.set("spark.sql.catalog.unity.token", "<token>")

// User creates external table - must specify location within schema boundary
spark.sql("""
  CREATE EXTERNAL TABLE unity.production.sales.customers (
    id INT,
    name STRING
  )
  USING DELTA
  LOCATION 's3://my-catalog-bucket/prod/sales/external/customers/'
""")

// Location validation happens via new endpoint before table creation
// Spark connector calls: POST /temporary-table-creation-credentials
// Response includes validated location and temporary credentials
```

#### Future: Managed Tables (when implemented)

```scala
// Create managed table - location auto-generated
spark.sql("""
  CREATE TABLE unity.production.sales.orders (
    order_id INT,
    customer_id INT,
    amount DECIMAL(10,2)
  )
  USING DELTA
""")
// Auto-generated location: s3://my-catalog-bucket/prod/sales/orders/
```

### 6. Security Model

#### External Tables
1. User must have: `CREATE_TABLE` + `USE_SCHEMA` + `USE_CATALOG`
2. Schema must have `storage_location` configured (deny by default)
3. Requested location must be within schema boundary
4. Credentials vended with `UPDATE` privilege (read + write)
5. Credentials scoped to specific table path

#### Managed Tables (Future)
1. Same privilege requirements
2. Schema must have `storage_location` configured
3. Location auto-generated under schema path
4. Credentials automatically vended for generated location

### 7. Migration Path

#### For Existing Installations

**Step 1: Database Migration**
```sql
-- Add nullable columns
ALTER TABLE uc_catalogs ADD COLUMN storage_root VARCHAR(255);
ALTER TABLE uc_schemas ADD COLUMN storage_location VARCHAR(1024);
```

**Step 2: Configure Existing Catalogs/Schemas (Optional)**
```bash
# Only needed if you want to enable table creation in existing schemas
bin/uc catalog update --name mycatalog --storage_root s3://my-bucket/
bin/uc schema update --catalog mycatalog --name myschema \
  --storage_location s3://my-bucket/myschema/
```

**Step 3: Deploy Updated Server**
- Existing tables continue to work (no changes to table storage)
- New table creation requires schema storage_location configuration
- Credentials vending uses new validation logic

### 8. Testing Strategy

#### Unit Tests

1. **StorageLocationValidatorTest**
   - Test boundary validation (same bucket, different bucket, path prefixes)
   - Test managed table path generation
   - Test edge cases (trailing slashes, special characters)

2. **TableRepositoryTest**
   - Test managed table creation (auto-generated path)
   - Test external table creation (with validation)
   - Test validation failures (location outside boundary)

3. **TemporaryTableCreationCredentialsServiceTest**
   - Test authorization checks
   - Test location validation
   - Test managed vs external table logic
   - Test credential vending

#### Integration Tests

1. **End-to-End Table Creation**
   - Create catalog with storage_root
   - Create schema with storage_location
   - Create external table with valid location
   - Create external table with invalid location (should fail)
   - Verify credentials work with actual S3

2. **Multi-Tenant Scenarios**
   - Multiple schemas in same catalog
   - User with access to multiple schemas
   - User without CREATE_TABLE privilege (should fail)

3. **Spark Connector Integration**
   - Create external tables via Spark SQL
   - Verify credential injection
   - Verify storage location validation

### 9. Implementation Phases

#### Minimal Viable Product (External Tables Only)
1. ✅ Database schema changes (catalog.storage_root, schema.storage_location)
2. ✅ API model updates (OpenAPI spec)
3. ✅ StorageLocationValidator utility
4. ✅ TemporaryTableCreationCredentialsService (new endpoint)
5. ✅ Update TableRepository to validate external table locations
6. ✅ CLI support for storage_root and storage_location
7. ✅ Documentation and examples

#### Phase 2: Managed Table Support (Future)
1. Remove managed table creation block in TableRepository
2. Implement auto-path generation
3. Add directory creation logic
4. Spark connector enhancements for managed tables
5. Additional testing

#### Phase 3: Advanced Features (Future)
1. External location allowlists (multiple allowed prefixes per schema)
2. Cross-bucket external tables (with additional configuration)
3. Storage location inheritance (schema inherits from catalog if not specified)
4. Audit logging for credential vending

### 10. Open Questions

1. **Should schema.storage_location be required or optional?**
   - Recommendation: Optional, but required for table creation
   - Allows schemas without tables (metadata-only)

2. **Should we support cross-bucket external tables?**
   - Recommendation: Not in MVP, add via external_locations allowlist later
   - Requires schema to list multiple allowed prefixes

3. **How to handle schema storage_location updates?**
   - Recommendation: Allow updates, but don't migrate existing tables
   - New tables use new location, existing tables stay where they are

4. **Should catalog.storage_root be required?**
   - Recommendation: Optional, only needed for managed tables
   - Allows catalogs without managed table support

5. **How to handle Spark connector changes?**
   - Recommendation: Spark connector should call new endpoint before CREATE TABLE
   - Inject returned credentials and use validated storage_location

### 11. Backwards Compatibility

- ✅ Existing tables continue to work (no schema changes to uc_tables)
- ✅ Existing external table creation works (location validation only applies if schema has storage_location)
- ✅ API additions are non-breaking (new optional fields, new endpoint)
- ✅ Managed table block remains until Phase 2 (no behavior change)

### 12. Documentation Updates Needed

1. **Server Configuration Guide**
   - How to configure catalog storage_root
   - How to configure schema storage_location
   - S3 bucket configuration requirements

2. **User Guide**
   - Creating external tables with location validation
   - Creating managed tables (Phase 2)
   - Multi-tenant setup examples

3. **API Documentation**
   - New endpoint documentation
   - Updated model documentation
   - Authorization requirements

4. **Spark Connector Guide**
   - How credential vending works
   - Location validation behavior
   - Error handling

## Summary

This design provides:
- ✅ Immediate solution for secure external table creation
- ✅ Foundation for managed tables (Phase 2)
- ✅ Multi-tenant security via schema storage boundaries
- ✅ Backwards compatibility with existing deployments
- ✅ Databricks-compatible storage hierarchy

The implementation can be done incrementally, with external table validation as MVP and managed table support following once the infrastructure is proven.
