# S3 Multi-Endpoint Configuration for Spark

Unity Catalog supports connecting to multiple S3-compatible storage endpoints (AWS S3, MinIO, Ceph, Dell ECS, etc.) within a single Spark session. This guide explains how to configure per-bucket S3 endpoints in the Spark connector.

## Overview

When tables in Unity Catalog use different S3-compatible storage backends (e.g., some tables in AWS S3, others in MinIO), Spark needs to know which endpoint to use for each bucket. Unity Catalog's Spark connector supports two configuration approaches:

1. **Automatic Configuration (D3)**: Fetch bucket configurations from Unity Catalog server automatically ✨ **Recommended**
2. **Manual Configuration (D2)**: Specify bucket endpoints via catalog properties (for advanced use cases or overrides)

## Automatic Configuration (D3) - Recommended

The Spark connector **automatically fetches** S3 bucket endpoint configurations from the Unity Catalog server during initialization. This is the easiest and recommended approach.

### Zero-Configuration Example

Simply connect to Unity Catalog - no S3 endpoint configuration needed:

```bash
spark-sql \
    --packages "org.apache.hadoop:hadoop-aws:3.4.1,io.delta:delta-spark_2.13:4.0.0,io.unitycatalog:unitycatalog-spark_2.13:0.3.0" \
    --conf "spark.sql.extensions=io.delta.sql.DeltaSparkSessionExtension" \
    --conf "spark.sql.catalog.spark_catalog=io.unitycatalog.spark.UCSingleCatalog" \
    --conf "spark.hadoop.fs.s3.impl=org.apache.hadoop.fs.s3a.S3AFileSystem" \
    --conf "spark.sql.catalog.unity=io.unitycatalog.spark.UCSingleCatalog" \
    --conf "spark.sql.catalog.unity.uri=http://localhost:8080" \
    --conf "spark.sql.defaultCatalog=unity"
```

**That's it!** The connector will:
1. Call `/api/2.1/unity-catalog/s3-bucket-configurations` during initialization
2. Automatically configure all S3 bucket endpoints
3. Apply per-bucket Hadoop configurations

### Verification

Check the Spark logs for confirmation:
```
INFO UCSingleCatalog: Applied S3 endpoint for bucket your-bucket: https://your-endpoint.com
INFO UCSingleCatalog: Auto-configured 1 S3 bucket(s) from Unity Catalog server
```

### How It Works

1. Unity Catalog server reads S3 configurations from `server.properties`
2. Server exposes bucket configurations via REST API (endpoint info only, no credentials)
3. Spark connector fetches configurations on startup
4. Per-bucket Hadoop configs are applied: `fs.s3a.bucket.{name}.endpoint`
5. Temporary credentials are still vended per-table for security

### Fallback Behavior

If the API call fails (e.g., older UC server version), the connector silently falls back to manual configuration without errors.

## Manual Configuration (D2) - Advanced

### Configuration Syntax

Configure per-bucket S3 endpoints using catalog properties in the format:

```
spark.sql.catalog.<catalog-name>.s3.bucket.<bucket-name>.<property>=<value>
```

### Supported Properties

| Property | Description | Example |
|----------|-------------|---------|
| `endpoint` | S3 endpoint URL | `http://minio:9000` |
| `path-style-access` | Use path-style access (required for MinIO/Ceph) | `true` or `false` |

### Example: Single MinIO Instance

```bash
spark-sql \
  --packages "io.unitycatalog:unitycatalog-spark_2.13:0.3.0" \
  --conf "spark.sql.catalog.unity=io.unitycatalog.spark.UCSingleCatalog" \
  --conf "spark.sql.catalog.unity.uri=http://localhost:8080" \
  --conf "spark.sql.catalog.unity.s3.bucket.my-bucket.endpoint=http://minio:9000" \
  --conf "spark.sql.catalog.unity.s3.bucket.my-bucket.path-style-access=true"
```

### Example: Multiple S3 Endpoints

```bash
spark-sql \
  --packages "io.unitycatalog:unitycatalog-spark_2.13:0.3.0" \
  --conf "spark.sql.catalog.unity=io.unitycatalog.spark.UCSingleCatalog" \
  --conf "spark.sql.catalog.unity.uri=http://localhost:8080" \
  --conf "spark.sql.catalog.unity.s3.bucket.bucket1.endpoint=http://minio1:9000" \
  --conf "spark.sql.catalog.unity.s3.bucket.bucket1.path-style-access=true" \
  --conf "spark.sql.catalog.unity.s3.bucket.bucket2.endpoint=http://minio2:9000" \
  --conf "spark.sql.catalog.unity.s3.bucket.bucket2.path-style-access=true" \
  --conf "spark.sql.catalog.unity.s3.bucket.aws-prod.endpoint=https://s3.us-west-2.amazonaws.com" \
  --conf "spark.sql.catalog.unity.s3.bucket.aws-prod.path-style-access=false"
```

### Example: PySpark

```python
from pyspark.sql import SparkSession

spark = SparkSession.builder \
    .appName("Unity Catalog Multi-Endpoint") \
    .config("spark.sql.catalog.unity", "io.unitycatalog.spark.UCSingleCatalog") \
    .config("spark.sql.catalog.unity.uri", "http://localhost:8080") \
    .config("spark.sql.catalog.unity.s3.bucket.my-bucket.endpoint", "http://minio:9000") \
    .config("spark.sql.catalog.unity.s3.bucket.my-bucket.path-style-access", "true") \
    .getOrCreate()

# Query tables from configured buckets
df = spark.sql("SELECT * FROM unity.schema1.table1")
df.show()
```

### Example: Spark Submit

```bash
spark-submit \
  --packages "io.unitycatalog:unitycatalog-spark_2.13:0.3.0" \
  --conf "spark.sql.catalog.unity=io.unitycatalog.spark.UCSingleCatalog" \
  --conf "spark.sql.catalog.unity.uri=http://localhost:8080" \
  --conf "spark.sql.catalog.unity.s3.bucket.my-bucket.endpoint=http://minio:9000" \
  --conf "spark.sql.catalog.unity.s3.bucket.my-bucket.path-style-access=true" \
  my_application.py
```

## How It Works

1. During catalog initialization, the Spark connector reads all properties starting with `s3.bucket.`
2. These properties are converted to Hadoop S3A per-bucket configurations:
   - `s3.bucket.mybucket.endpoint` → `fs.s3a.bucket.mybucket.endpoint`
   - `s3.bucket.mybucket.path-style-access` → `fs.s3a.bucket.mybucket.path.style.access`
3. When Spark accesses tables in `s3://mybucket/`, Hadoop automatically uses the bucket-specific configuration
4. Temporary credentials are still vended per-table for security

## Finding Bucket Names

To determine which bucket names to configure, check your table storage locations:

```sql
-- In spark-sql or PySpark
DESCRIBE EXTENDED unity.schema1.table1;

-- Look for the "Location" field, e.g.:
-- Location: s3://my-bucket/path/to/table
```

Or via Unity Catalog API:
```bash
curl http://localhost:8080/api/2.1/unity-catalog/tables/unity.schema1.table1 \
  | jq .storage_location
```

## Troubleshooting

### Error: "No AWS Credentials"
Make sure your Unity Catalog server is configured with proper S3 credentials in `etc/conf/server.properties`.

### Error: "403 Forbidden" or "Connection Refused"
- Verify the endpoint URL is correct and accessible from Spark workers
- Check that `path-style-access` is set to `true` for MinIO/Ceph

### Tables in Same Bucket Work, Different Buckets Fail
- Ensure you've configured all bucket endpoints your tables use
- Check Spark logs for "Applied S3 bucket configuration" messages

## Configuration Priority

The Spark connector applies configurations in this order:
1. **Automatic from server** (D3) - fetched from `/s3-bucket-configurations`
2. **Manual catalog properties** (D2) - override automatic if specified

This allows you to use automatic configuration for most buckets while manually overriding specific ones if needed.

## Related Configuration

### Unity Catalog Server Configuration

The UC server must be configured with S3 storage configurations in `etc/conf/server.properties`:

```properties
s3.bucketPath.0=s3://my-bucket
s3.region.0=us-east-1
s3.awsRoleArn.0=arn:aws:iam::123456789012:role/my-role
s3.s3ServiceEndpoint.0=http://minio:9000
s3.stsEndpoint.0=http://minio:9000
s3.pathStyleAccess.0=true
```

See [Server Configuration](../server/s3-configuration.md) for more details.