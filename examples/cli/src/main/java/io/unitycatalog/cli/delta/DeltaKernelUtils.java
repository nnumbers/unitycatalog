package io.unitycatalog.cli.delta;

import static io.unitycatalog.cli.utils.CliUtils.EMPTY;

import de.vandermeer.asciitable.AsciiTable;
import io.delta.kernel.*;
import io.delta.kernel.data.Row;
import io.delta.kernel.defaults.engine.DefaultEngine;
import io.delta.kernel.engine.Engine;
import io.delta.kernel.types.*;
import io.delta.kernel.utils.CloseableIterable;
import io.unitycatalog.client.model.AwsCredentials;
import io.unitycatalog.client.model.ColumnInfo;
import java.net.URI;
import java.util.*;
import java.util.stream.IntStream;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;

/**
 * Utility class to create and read Delta tables. The create method creates a Delta table with the
 * given schema at the given path. The create method just initializes the delta log and does not
 * write any data to the table. The read method reads the data from the Delta table The code has
 * evolved from examples provided in <a
 * href="https://github.com/delta-io/delta/tree/master/kernel/examples/kernel-examples/src/main/java/io/delta/kernel/examples">Delta
 * examples</a>
 */
public class DeltaKernelUtils {

  public static String createDeltaTable(
      String tablePath, List<ColumnInfo> columns, AwsCredentials awsTempCredentials) {
    try {
      URI tablePathUri = URI.create(tablePath);
      Engine engine = getEngine(tablePathUri, awsTempCredentials);
      Table table = Table.forPath(engine, substituteSchemeForS3(tablePath));
      // construct the schema
      StructType tableSchema = getSchema(columns);
      TransactionBuilder txnBuilder =
          table.createTransactionBuilder(engine, "UnityCatalogCli", Operation.CREATE_TABLE);
      // Set the schema of the new table on the transaction builder
      txnBuilder = txnBuilder.withSchema(engine, tableSchema);
      // Build the transaction
      Transaction txn = txnBuilder.build(engine);
      // create an empty table
      TransactionCommitResult commitResult = txn.commit(engine, CloseableIterable.emptyIterable());
      if (commitResult.getVersion() >= 0) {
        System.out.println("Table created successfully at: " + tablePath);
      } else {
        throw new RuntimeException("Table creation failed");
      }
    } catch (Exception e) {
      throw new IllegalArgumentException("Failed to create delta table", e);
    }
    return EMPTY;
  }

  public static String substituteSchemeForS3(String tablePath) {
    return tablePath.replace("s3://", "s3a://");
  }

  public static Engine getEngine(URI tablePathUri, AwsCredentials awsTempCredentials) {
    return DefaultEngine.create(getHDFSConfiguration(tablePathUri, awsTempCredentials));
  }

  public static FileSystem getFileSystem(URI tablePathURI, Configuration conf) {
    try {
      return FileSystem.get(tablePathURI, conf);
    } catch (Exception e) {
      throw new IllegalArgumentException("Failed to get file system", e);
    }
  }

  public static Configuration getHDFSConfiguration(
      URI tablePathUri, AwsCredentials awsTempCredentials) {
    Configuration conf = new Configuration();
    if (tablePathUri.getScheme() != null
        && tablePathUri.getScheme().equals("s3")
        && awsTempCredentials == null) {
      throw new IllegalArgumentException("AWS temporary credentials are missing");
    }
    if (tablePathUri.getScheme().equals("s3")) {
      conf.set("fs.s3a.access.key", awsTempCredentials.getAccessKeyId());
      conf.set("fs.s3a.secret.key", awsTempCredentials.getSecretAccessKey());
      conf.set("fs.s3a.session.token", awsTempCredentials.getSessionToken());
      conf.set("fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem");

      // Disable caching to ensure fresh connections with correct credentials
      conf.set("fs.s3a.impl.disable.cache", "true");

      // Add S3 endpoint if provided (for MinIO and other S3-compatible storage)
      if (awsTempCredentials.getS3ServiceEndpoint() != null
          && !awsTempCredentials.getS3ServiceEndpoint().isEmpty()) {
        conf.set("fs.s3a.endpoint", awsTempCredentials.getS3ServiceEndpoint());
        System.out.println("Configured S3A endpoint: " + awsTempCredentials.getS3ServiceEndpoint());
        // Determine SSL based on endpoint URL
        conf.set(
            "fs.s3a.connection.ssl.enabled",
            awsTempCredentials.getS3ServiceEndpoint().startsWith("https") ? "true" : "false");
      }

      // Use path-style access flag from credentials if provided, default to true
      if (awsTempCredentials.getPathStyleAccess() != null) {
        conf.set("fs.s3a.path.style.access", awsTempCredentials.getPathStyleAccess().toString());
      } else {
        conf.set("fs.s3a.path.style.access", "true");
      }

      System.out.println("S3A Configuration:");
      System.out.println("  Endpoint: " + conf.get("fs.s3a.endpoint", "default AWS"));
      System.out.println("  Path style: " + conf.get("fs.s3a.path.style.access"));
      System.out.println("  SSL enabled: " + conf.get("fs.s3a.connection.ssl.enabled", "true"));
    } else if (tablePathUri.getScheme().equals("file")) {
      conf.set("fs.file.impl", "org.apache.hadoop.fs.LocalFileSystem");
    } else {
      throw new IllegalArgumentException("Unsupported URI scheme: " + tablePathUri.getScheme());
    }
    return conf;
  }

  public static String readDeltaTable(
      String tablePath, AwsCredentials awsCredentials, int maxResults) {
    System.out.println("Reading Delta table from: " + tablePath);
    if (awsCredentials != null && awsCredentials.getS3ServiceEndpoint() != null) {
      System.out.println("Using S3 endpoint: " + awsCredentials.getS3ServiceEndpoint());
      System.out.println("Path style access: " + awsCredentials.getPathStyleAccess());
    }

    Engine engine = getEngine(URI.create(tablePath), awsCredentials);
    try {
      String s3aPath = substituteSchemeForS3(tablePath);
      System.out.println("Converted path for Delta: " + s3aPath);

      Table table = Table.forPath(engine, s3aPath);
      Snapshot snapshot = table.getLatestSnapshot(engine);
      StructType readSchema = snapshot.getSchema();
      Object[] schema =
          readSchema.fields().stream()
              .map(x -> x.getName() + "(" + x.getDataType().toString() + ")")
              .toArray(String[]::new);
      AsciiTable at = new AsciiTable();

      // Check if table is too wide and use a simplified format for very wide tables
      if (schema.length > 10) {
        // For very wide tables, use a vertical format
        StringBuilder output = new StringBuilder();
        output
            .append("\n=== Table Data (Vertical Format - ")
            .append(schema.length)
            .append(" columns) ===\n");

        // might need to prune it later
        ScanBuilder scanBuilder = snapshot.getScanBuilder().withReadSchema(readSchema);
        List<Row> rowData =
            DeltaKernelReadUtils.readData(engine, readSchema, scanBuilder.build(), maxResults);

        int rowNum = 1;
        for (Row row : rowData) {
          output.append("\n--- Row ").append(rowNum++).append(" ---\n");
          for (int i = 0; i < schema.length; i++) {
            String columnName = readSchema.at(i).getName();
            String dataType = readSchema.at(i).getDataType().toString();
            String value = DeltaKernelReadUtils.getValue(row, i);
            output.append(String.format("%-30s (%s): %s\n", columnName, dataType, value));
          }
        }
        output.append("\nTotal rows: ").append(rowData.size()).append("\n");
        return output.toString();
      }

      // For normal tables, use ASCII table with truncated values
      at.addRule();
      at.addRow(schema);
      at.addRule();
      // might need to prune it later
      ScanBuilder scanBuilder = snapshot.getScanBuilder().withReadSchema(readSchema);
      List<Row> rowData =
          DeltaKernelReadUtils.readData(engine, readSchema, scanBuilder.build(), maxResults);
      for (Row row : rowData) {
        Object[] rowValues =
            IntStream.range(0, schema.length)
                .mapToObj(
                    colOrdinal -> {
                      String value = DeltaKernelReadUtils.getValue(row, colOrdinal);
                      // Truncate long values to prevent table width issues
                      if (value != null && value.length() > 50) {
                        return value.substring(0, 47) + "...";
                      }
                      return value;
                    })
                .toArray();
        at.addRow(rowValues);
        at.addRule();
      }

      // Set a wider renderer width for tables with many columns
      at.getContext().setWidth(Math.max(80, schema.length * 15));

      return at.render();
    } catch (Exception e) {
      System.err.println("Error reading Delta table: " + e.getClass().getName());
      System.err.println("Error message: " + e.getMessage());
      if (e.getCause() != null) {
        System.err.println("Root cause: " + e.getCause().getClass().getName());
        System.err.println("Root cause message: " + e.getCause().getMessage());
      }
      e.printStackTrace();
      throw new IllegalArgumentException("Failed to read delta table from " + tablePath, e);
    }
  }

  // TODO : INTERVAL, CHAR and NULL, ARRAY, MAP, STRUCT
  public static StructType getSchema(List<ColumnInfo> columns) {
    StructType structType = new StructType();
    for (ColumnInfo column : columns) {
      DataType dataType = getDataType(column);
      structType = structType.add(column.getName(), dataType);
    }
    return structType;
  }

  public static DataType getDataType(ColumnInfo column) {
    if (column.getTypeName() == null) {
      throw new IllegalArgumentException("Column type is missing: " + column.getName());
    }
    return findBasicTypeFromString(column.getTypeName().toString());
  }

  public static DataType findBasicTypeFromString(String typeText) {
    DataType dataType = null;
    switch (typeText) {
      case "INT":
        dataType = IntegerType.INTEGER;
        break;
      case "TIMESTAMP_NTZ":
        dataType = TimestampNTZType.TIMESTAMP_NTZ;
        break;
      case "STRING":
      case "DOUBLE":
      case "BOOLEAN":
      case "LONG":
      case "FLOAT":
      case "SHORT":
      case "BYTE":
      case "DATE":
      case "TIMESTAMP":
      case "BINARY":
      case "DECIMAL":
        dataType = BasePrimitiveType.createPrimitive(typeText.toLowerCase(Locale.ROOT));
        break;
      default:
        throw new IllegalArgumentException("Unsupported basic data type: " + typeText);
    }
    return dataType;
  }
}
