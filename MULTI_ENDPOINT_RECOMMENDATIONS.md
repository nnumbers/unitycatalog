# Multi-Endpoint S3 Storage Support for Unity Catalog Spark Connector

## Problem Statement

Unity Catalog supports multiple S3-compatible storage configurations with different endpoints (e.g., MinIO, Ceph, multiple AWS regions), but Spark's Hadoop configuration is global. When users query tables from different schemas that use different S3 endpoints, the current implementation fails because:

1. Delta Lake reads transaction logs using global Hadoop config before table-specific credentials are applied
2. Spark's FileSystem cache doesn't support multiple endpoints for the same scheme (s3a://)
3. Credentials are injected per-table, but endpoint configuration needs to be global

Example scenario:
- Table in Schema A: `s3://bucket1/path` → uses `https://endpoint1.com`
- Table in Schema B: `s3://bucket2/path` → uses `https://endpoint2.com`
- Both need to work in the same Spark session

## Solution Options Analysis

### Option 1: Custom FileSystem Implementation with Credential Router
**Concept**: Create a custom FileSystem wrapper that intercepts S3 requests and routes to correct endpoint based on path.

```scala
class UnityCatalogS3AFileSystem extends S3AFileSystem {
  override def initialize(uri: URI, conf: Configuration): Unit = {
    val endpoint = UnityCatalogCredentialRouter.getEndpointForPath(uri)
    if (endpoint.isDefined) {
      conf.set("fs.s3a.endpoint", endpoint.get)
    }
    super.initialize(uri, conf)
  }
}
```

**Pros:**
- Transparent to users
- Supports unlimited endpoints
- Works with all Spark operations

**Cons:**
- Requires registering custom filesystem
- May have performance impact from endpoint lookups
- Complex implementation

### Option 2: Path-Prefix-Based Configuration Injection
**Concept**: Use Spark's native per-bucket Hadoop configurations.

```scala
// For each bucket/path prefix:
hadoopConf.set(s"fs.s3a.bucket.${bucketName}.endpoint", config.endpoint)
hadoopConf.set(s"fs.s3a.bucket.${bucketName}.path.style.access", "true")
```

**Pros:**
- Uses native Hadoop S3A features
- No custom filesystem needed
- Good performance

**Cons:**
- Requires knowing all buckets upfront
- May expose credentials globally

### Option 3: Lazy Credential Injection with Table Registry
**Concept**: Maintain registry of accessed tables and inject configurations just-in-time.

```scala
object UnityCatalogTableRegistry {
  private val tableConfigs = mutable.Map[String, StorageConfig]()

  def registerTable(tablePath: String, config: StorageConfig): Unit = {
    tableConfigs(tablePath) = config
    updateHadoopConfig(tablePath, config)
  }
}
```

**Pros:**
- Configurations added as tables are accessed
- No upfront configuration needed

**Cons:**
- First access might fail if Delta reads logs before loadTable
- Requires careful synchronization

### Option 4: Unity Catalog Catalog-Wide Configuration with Override (RECOMMENDED)
**Concept**: Add catalog-level storage configurations fetched from server during initialization.

```scala
// During catalog initialization:
val storageConfigs = fetchStorageConfigurations() // New API call
storageConfigs.foreach { config =>
  val bucket = extractBucket(config.pathPrefix)
  hadoopConf.set(s"fs.s3a.bucket.${bucket}.endpoint", config.endpoint)
  hadoopConf.set(s"fs.s3a.bucket.${bucket}.path.style.access", config.pathStyleAccess)
}
```

**Pros:**
- Clean configuration model
- Supports multiple endpoints elegantly
- Credentials remain temporary/scoped
- Works with existing S3A features

**Cons:**
- Requires knowing configurations upfront
- Needs API enhancement

### Option 5: Hybrid - Smart Credential Provider
**Concept**: Custom AWS credential provider that queries Unity Catalog on-demand.

```scala
class UnityCatalogAWSCredentialProvider extends AWSCredentialsProvider {
  override def getCredentials(): AWSCredentials = {
    val currentPath = ThreadContext.getCurrentPath()
    val creds = UnityCatalogClient.getCredentialsForPath(currentPath)
    new BasicSessionCredentials(creds.accessKey, creds.secretKey, creds.sessionToken)
  }
}
```

**Pros:**
- Very elegant - credentials fetched on demand
- No configuration needed

**Cons:**
- Complex implementation
- Need to handle endpoint configuration separately

## Recommended Implementation Plan (Option 4)

### Phase 1: API Enhancement

1. **Add new API endpoint**: `/api/2.1/unity-catalog/storage-configurations`
   ```json
   {
     "configurations": [
       {
         "pathPrefix": "s3://bucket1/",
         "endpoint": "https://endpoint1.com",
         "region": "us-east-1",
         "pathStyleAccess": true
       },
       {
         "pathPrefix": "s3://bucket2/",
         "endpoint": "https://endpoint2.com",
         "region": "us-west-2",
         "pathStyleAccess": false
       }
     ]
   }
   ```

2. **Server implementation**: Query server properties and return configs user has access to

### Phase 2: Spark Connector Enhancement

1. **Update UCSingleCatalog.initialize():**
   ```scala
   override def initialize(name: String, options: CaseInsensitiveStringMap): Unit = {
     // ... existing initialization ...

     // Fetch and apply storage configurations
     try {
       val storageApi = new StorageConfigurationsApi(apiClient)
       val configs = storageApi.listStorageConfigurations()
       applyStorageConfigurations(configs)
     } catch {
       case e: Exception =>
         logWarning("Could not fetch storage configurations", e)
         // Fall back to manual configuration
     }
   }

   private def applyStorageConfigurations(configs: List[StorageConfig]): Unit = {
     val hadoopConf = SparkSession.active.sparkContext.hadoopConfiguration

     configs.foreach { config =>
       val bucket = extractBucketFromPath(config.pathPrefix)

       // Apply per-bucket S3A configuration
       hadoopConf.set(s"fs.s3a.bucket.${bucket}.endpoint", config.endpoint)
       hadoopConf.set(s"fs.s3a.bucket.${bucket}.path.style.access", config.pathStyleAccess.toString)
       hadoopConf.set(s"fs.s3a.bucket.${bucket}.connection.ssl.enabled",
                      if (config.endpoint.startsWith("https")) "true" else "false")

       // Don't set credentials here - let temporary credentials handle that per-table
     }
   }
   ```

2. **Support manual override via Spark conf:**
   ```scala
   // Allow manual configuration as fallback
   spark.sql.catalog.unity.s3.bucket.bucket1.endpoint=https://endpoint1.com
   spark.sql.catalog.unity.s3.bucket.bucket2.endpoint=https://endpoint2.com
   ```

### Phase 3: Credential Handling (Keep Current Approach)

- Continue using temporary credentials per-table in `loadTable()`
- Credentials are injected into table storage properties
- Per-bucket endpoint configuration ensures correct routing

### Benefits of This Approach

1. **Clean Separation**: Endpoints (global) vs credentials (per-table)
2. **Scalable**: Supports unlimited storage configurations
3. **Secure**: Temporary credentials with proper scoping
4. **Compatible**: Works with existing Hadoop S3A features
5. **User-Friendly**: No manual configuration needed in most cases
6. **Flexible**: Supports manual override when needed

### Usage After Implementation

Users would only need:
```bash
spark-sql --packages "io.unitycatalog:unitycatalog-spark_2.13:0.3.0" \
    --conf "spark.sql.catalog.unity=io.unitycatalog.spark.UCSingleCatalog" \
    --conf "spark.sql.catalog.unity.uri=http://localhost:8080" \
    --conf "spark.sql.defaultCatalog=unity"
```

No manual S3 endpoint or credential configuration required!

## Current Workaround

Until this is implemented, users must manually configure all S3 endpoints:
```bash
--conf "spark.hadoop.fs.s3a.endpoint=https://endpoint.com"
--conf "spark.hadoop.fs.s3a.access.key=xxx"
--conf "spark.hadoop.fs.s3a.secret.key=yyy"
```

## Files to Modify for Implementation

1. **API Specification**: `api/all.yaml`
   - Add StorageConfiguration model
   - Add /storage-configurations endpoint

2. **Server Side**:
   - `server/src/main/java/io/unitycatalog/server/service/StorageConfigurationService.java` (new)
   - `server/src/main/java/io/unitycatalog/server/sdk/storage/StorageConfigurationsApi.java` (new)

3. **Spark Connector**:
   - `connectors/spark/src/main/scala/io/unitycatalog/spark/UCSingleCatalog.scala`
   - Add storage configuration fetching and application logic

4. **Configuration**:
   - `etc/conf/server.properties` - Already supports multiple S3 configs
   - Just need to expose via API

This approach provides an elegant solution to the multi-endpoint challenge while maintaining security and usability.