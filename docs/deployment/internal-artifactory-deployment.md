# Internal Artifactory Deployment Guide

This guide explains how to deploy Unity Catalog Spark Connector with S3 multi-endpoint support to your internal Artifactory repository and how users can consume it.

## For Deployment Teams

### Prerequisites

- Access to your Artifactory instance with publish permissions
- Unity Catalog repository with S3 multi-endpoint changes
- SBT installed

### Step 1: Build the Assembly JAR

Build the Spark connector with all dependencies:

```bash
cd /path/to/unitycatalog
build/sbt "project spark" assembly
```

This creates: `connectors/spark/target/scala-2.13/unitycatalog-spark-assembly-0.3.0-SNAPSHOT.jar`

### Step 2: Configure Artifactory Credentials

Create or update `~/.sbt/.credentials`:

```
realm=Artifactory Realm
host=artifactory.yourcompany.com
user=YOUR_USERNAME
password=YOUR_PASSWORD_OR_TOKEN
```

**Important**: Replace with your actual Artifactory host and credentials.

### Step 3: Configure SBT Publishing (Optional)

If you want to use `sbt publish`, add to `build.sbt` in the spark project section:

```scala
publishTo := Some("Internal Artifactory" at "https://artifactory.yourcompany.com/artifactory/libs-release-local")
credentials += Credentials(Path.userHome / ".sbt" / ".credentials")
publishMavenStyle := true
```

Then publish:
```bash
build/sbt "project spark" publish
```

### Step 4: Manual Upload to Artifactory

Alternatively, upload the assembly JAR manually:

```bash
# Set your Artifactory details
ARTIFACTORY_URL="https://artifactory.yourcompany.com/artifactory"
REPOSITORY="libs-release-local"
GROUP_PATH="io/unitycatalog"
ARTIFACT_ID="unitycatalog-spark_2.13"
VERSION="0.3.0-SNAPSHOT"

# Upload the assembly JAR
curl -u username:password \
  -T connectors/spark/target/scala-2.13/unitycatalog-spark-assembly-${VERSION}.jar \
  "${ARTIFACTORY_URL}/${REPOSITORY}/${GROUP_PATH}/${ARTIFACT_ID}/${VERSION}/${ARTIFACT_ID}-${VERSION}.jar"

# Upload POM (if available)
curl -u username:password \
  -T connectors/spark/target/scala-2.13/unitycatalog-spark-${VERSION}.pom \
  "${ARTIFACTORY_URL}/${REPOSITORY}/${GROUP_PATH}/${ARTIFACT_ID}/${VERSION}/${ARTIFACT_ID}-${VERSION}.pom"
```

### Step 5: Verify Upload

Check that the artifact is available:

```bash
curl -u username:password \
  "https://artifactory.yourcompany.com/artifactory/libs-release-local/io/unitycatalog/unitycatalog-spark_2.13/0.3.0-SNAPSHOT/"
```

### Recommended Versioning

Consider using a custom version suffix to distinguish from official releases:

- `0.3.0-internal-SNAPSHOT` - For development/testing
- `0.3.0-internal-1` - For stable internal releases
- `0.3.0+s3-multiendpoint` - Version with feature tag

Update the version in `version.sbt` before building:
```scala
ThisBuild / version := "0.3.0-internal-1"
```

---

## For End Users

### Prerequisites

- Spark 3.3+ installed
- Access to your company's Artifactory repository
- Unity Catalog server running with S3 configurations

### Option 1: Using Spark Packages (Recommended)

If your Artifactory supports Maven repository format:

```bash
spark-sql \
    --repositories "https://artifactory.yourcompany.com/artifactory/libs-release-local" \
    --packages "org.apache.hadoop:hadoop-aws:3.4.1,io.delta:delta-spark_2.13:4.0.0,io.unitycatalog:unitycatalog-spark_2.13:0.3.0-SNAPSHOT" \
    --conf "spark.sql.extensions=io.delta.sql.DeltaSparkSessionExtension" \
    --conf "spark.sql.catalog.spark_catalog=io.unitycatalog.spark.UCSingleCatalog" \
    --conf "spark.hadoop.fs.s3.impl=org.apache.hadoop.fs.s3a.S3AFileSystem" \
    --conf "spark.sql.catalog.unity=io.unitycatalog.spark.UCSingleCatalog" \
    --conf "spark.sql.catalog.unity.uri=http://localhost:8080" \
    --conf "spark.sql.defaultCatalog=unity"
```

#### Fish Shell Version:
```fish
spark-sql \
    --repositories "https://artifactory.yourcompany.com/artifactory/libs-release-local" \
    --packages "org.apache.hadoop:hadoop-aws:3.4.1,io.delta:delta-spark_2.13:4.0.0,io.unitycatalog:unitycatalog-spark_2.13:0.3.0-SNAPSHOT" \
    --conf "spark.sql.extensions=io.delta.sql.DeltaSparkSessionExtension" \
    --conf "spark.sql.catalog.spark_catalog=io.unitycatalog.spark.UCSingleCatalog" \
    --conf "spark.hadoop.fs.s3.impl=org.apache.hadoop.fs.s3a.S3AFileSystem" \
    --conf "spark.sql.catalog.unity=io.unitycatalog.spark.UCSingleCatalog" \
    --conf "spark.sql.catalog.unity.uri=http://localhost:8080" \
    --conf "spark.sql.defaultCatalog=unity"
```

### Option 2: Using Direct JAR Download

Download the JAR from Artifactory and use it directly:

```bash
# Download the JAR
curl -u username:password \
  -o unitycatalog-spark-assembly.jar \
  "https://artifactory.yourcompany.com/artifactory/libs-release-local/io/unitycatalog/unitycatalog-spark_2.13/0.3.0-SNAPSHOT/unitycatalog-spark_2.13-0.3.0-SNAPSHOT.jar"

# Use with Spark
spark-sql \
    --jars "unitycatalog-spark-assembly.jar" \
    --packages "org.apache.hadoop:hadoop-aws:3.4.1,io.delta:delta-spark_2.13:4.0.0" \
    --conf "spark.sql.extensions=io.delta.sql.DeltaSparkSessionExtension" \
    --conf "spark.sql.catalog.spark_catalog=io.unitycatalog.spark.UCSingleCatalog" \
    --conf "spark.hadoop.fs.s3.impl=org.apache.hadoop.fs.s3a.S3AFileSystem" \
    --conf "spark.sql.catalog.unity=io.unitycatalog.spark.UCSingleCatalog" \
    --conf "spark.sql.catalog.unity.uri=http://localhost:8080" \
    --conf "spark.sql.defaultCatalog=unity"
```

### Authentication Setup

If your Artifactory requires authentication for downloads:

#### Maven Settings (~/.m2/settings.xml)

```xml
<settings>
  <servers>
    <server>
      <id>internal-artifactory</id>
      <username>YOUR_USERNAME</username>
      <password>YOUR_PASSWORD_OR_TOKEN</password>
    </server>
  </servers>
  <profiles>
    <profile>
      <id>artifactory</id>
      <repositories>
        <repository>
          <id>internal-artifactory</id>
          <url>https://artifactory.yourcompany.com/artifactory/libs-release-local</url>
        </repository>
      </repositories>
    </profile>
  </profiles>
  <activeProfiles>
    <activeProfile>artifactory</activeProfile>
  </activeProfiles>
</settings>
```

#### SBT Settings (~/.sbt/.credentials)

```
realm=Artifactory Realm
host=artifactory.yourcompany.com
user=YOUR_USERNAME
password=YOUR_PASSWORD_OR_TOKEN
```

### PySpark Example

```python
from pyspark.sql import SparkSession

spark = SparkSession.builder \
    .appName("Unity Catalog S3 Multi-Endpoint") \
    .config("spark.jars.repositories", "https://artifactory.yourcompany.com/artifactory/libs-release-local") \
    .config("spark.jars.packages", "org.apache.hadoop:hadoop-aws:3.4.1,io.delta:delta-spark_2.13:4.0.0,io.unitycatalog:unitycatalog-spark_2.13:0.3.0-SNAPSHOT") \
    .config("spark.sql.extensions", "io.delta.sql.DeltaSparkSessionExtension") \
    .config("spark.sql.catalog.spark_catalog", "io.unitycatalog.spark.UCSingleCatalog") \
    .config("spark.hadoop.fs.s3.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem") \
    .config("spark.sql.catalog.unity", "io.unitycatalog.spark.UCSingleCatalog") \
    .config("spark.sql.catalog.unity.uri", "http://localhost:8080") \
    .config("spark.sql.defaultCatalog", "unity") \
    .getOrCreate()

# Query tables - S3 endpoints are automatically configured!
df = spark.sql("SELECT * FROM unity.default.nyc_taxi_features LIMIT 10")
df.show()
```

### Features Included

This internal build includes:

✅ **Automatic S3 Endpoint Configuration (D3)**: Zero configuration needed - endpoints are fetched from Unity Catalog server automatically

✅ **Manual S3 Endpoint Configuration (D2)**: Override specific buckets if needed using catalog properties:
```bash
--conf "spark.sql.catalog.unity.s3.bucket.BUCKET_NAME.endpoint=https://your-s3-endpoint.com" \
--conf "spark.sql.catalog.unity.s3.bucket.BUCKET_NAME.path-style-access=true"
```

✅ **Multi-Endpoint Support**: Query tables from multiple S3-compatible storage systems (AWS S3, MinIO, Ceph, Dell ECS, etc.) in the same Spark session

✅ **Secure Credential Vending**: Temporary, scoped credentials are still vended per-table for security

### Verification

After starting Spark, you should see log messages indicating successful configuration:

```
INFO UCSingleCatalog: Applied S3 endpoint for bucket your-bucket: https://your-endpoint.com
INFO UCSingleCatalog: Auto-configured N S3 bucket(s) from Unity Catalog server
```

### Troubleshooting

#### JAR Not Found
- Verify the repository URL is correct
- Check authentication credentials in `~/.m2/settings.xml`
- Try accessing the Artifactory URL directly in a browser

#### Class Not Found: io.unitycatalog.spark.UCSingleCatalog
- Ensure you're using the assembly JAR (includes all dependencies)
- Verify the JAR was downloaded completely (check file size)
- Try using `--jars` with a local copy instead of `--packages`

#### S3 Endpoint Not Applied
- Check that Unity Catalog server is running: `curl http://localhost:8080/api/2.1/unity-catalog/s3-bucket-configurations`
- Verify server.properties has S3 configurations set up correctly
- Check Spark logs for "Auto-configured" messages

#### Authentication Errors
- Verify credentials in Maven settings or SBT credentials file
- Check that your user has read access to the Artifactory repository
- Test authentication: `curl -u username:password https://artifactory.yourcompany.com/artifactory/libs-release-local/`

---

## Support

For issues with:
- **Deployment**: Contact your DevOps/Platform team
- **Unity Catalog Configuration**: See [S3 Multi-Endpoint Configuration](../usage/s3-multi-endpoint-configuration.md)
- **Feature Questions**: Contact the Unity Catalog team

## Version Information

- **Unity Catalog Version**: 0.3.0-SNAPSHOT (or your custom version)
- **Spark Compatibility**: 3.3+, 3.4, 3.5
- **Scala Version**: 2.13
- **Features**: S3 Multi-Endpoint Support (D2 + D3)