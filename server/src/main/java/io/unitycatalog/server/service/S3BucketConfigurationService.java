package io.unitycatalog.server.service;

import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.server.annotation.ExceptionHandler;
import com.linecorp.armeria.server.annotation.Get;
import io.unitycatalog.server.exception.GlobalExceptionHandler;
import io.unitycatalog.server.model.ListS3BucketConfigurationsResponse;
import io.unitycatalog.server.model.S3BucketConfiguration;
import io.unitycatalog.server.service.credential.aws.S3StorageConfig;
import io.unitycatalog.server.utils.ServerProperties;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service for managing S3 bucket configurations. Provides endpoint configuration information (not
 * credentials) for S3-compatible storage.
 */
@ExceptionHandler(GlobalExceptionHandler.class)
public class S3BucketConfigurationService {

  private final ServerProperties serverProperties;

  public S3BucketConfigurationService(ServerProperties serverProperties) {
    this.serverProperties = serverProperties;
  }

  /**
   * List all S3 bucket configurations available on this Unity Catalog server. Returns endpoint
   * information (no credentials) for all configured S3 buckets.
   *
   * @return List of S3 bucket configurations
   */
  @Get("")
  public HttpResponse listS3BucketConfigurations() {
    Map<String, S3StorageConfig> s3Configs = serverProperties.getS3Configurations();
    List<S3BucketConfiguration> configurations = new ArrayList<>();

    for (Map.Entry<String, S3StorageConfig> entry : s3Configs.entrySet()) {
      String bucketPath = entry.getKey();
      S3StorageConfig config = entry.getValue();

      // Extract bucket name from s3://bucket-name/path
      String bucketName = extractBucketName(bucketPath);

      S3BucketConfiguration bucketConfig = new S3BucketConfiguration();
      bucketConfig.setBucketName(bucketName);

      if (config.getS3ServiceEndpoint() != null && !config.getS3ServiceEndpoint().isEmpty()) {
        bucketConfig.setEndpoint(config.getS3ServiceEndpoint());
      }

      if (config.getRegion() != null) {
        bucketConfig.setRegion(config.getRegion());
      }

      if (config.getPathStyleAccess() != null) {
        bucketConfig.setPathStyleAccess(config.getPathStyleAccess());
      }

      configurations.add(bucketConfig);
    }

    ListS3BucketConfigurationsResponse response = new ListS3BucketConfigurationsResponse();
    response.setConfigurations(configurations);
    return HttpResponse.ofJson(response);
  }

  /**
   * Extract bucket name from S3 path. Converts "s3://bucket-name/path" to "bucket-name"
   *
   * @param s3Path Full S3 path (e.g., "s3://my-bucket/path/to/data")
   * @return Bucket name (e.g., "my-bucket")
   */
  private String extractBucketName(String s3Path) {
    try {
      URI uri = URI.create(s3Path);
      String host = uri.getHost();
      return host != null ? host : s3Path;
    } catch (Exception e) {
      // Fallback: try to extract bucket name manually
      if (s3Path.startsWith("s3://")) {
        String withoutScheme = s3Path.substring(5);
        int slashIndex = withoutScheme.indexOf('/');
        return slashIndex > 0 ? withoutScheme.substring(0, slashIndex) : withoutScheme;
      }
      return s3Path;
    }
  }
}
