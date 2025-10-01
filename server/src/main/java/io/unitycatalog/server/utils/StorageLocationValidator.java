package io.unitycatalog.server.utils;

import io.unitycatalog.server.exception.BaseException;
import io.unitycatalog.server.exception.ErrorCode;
import java.net.URI;

/** Utility class for validating and generating storage locations for tables and schemas. */
public class StorageLocationValidator {

  /**
   * Validates that a storage location is within an allowed boundary.
   *
   * <p>This is used to enforce multi-tenant security by ensuring users can only create tables
   * within their schema's designated storage boundary.
   *
   * @param location The location to validate (e.g., s3://bucket/schema/external/table/)
   * @param boundary The allowed boundary (e.g., s3://bucket/schema/)
   * @return true if location is under boundary, false otherwise
   */
  public static boolean isWithinBoundary(String location, String boundary) {
    if (boundary == null || boundary.isEmpty()) {
      return false; // No boundary configured = deny by default
    }

    if (location == null || location.isEmpty()) {
      return false;
    }

    URI locationUri;
    URI boundaryUri;
    try {
      locationUri = URI.create(location);
      boundaryUri = URI.create(boundary);
    } catch (IllegalArgumentException e) {
      return false; // Invalid URI
    }

    // Must be same scheme (s3://, s3a://, etc.)
    if (locationUri.getScheme() == null
        || !locationUri.getScheme().equals(boundaryUri.getScheme())) {
      return false;
    }

    // Must be same bucket (for s3://)
    if (locationUri.getHost() == null || !locationUri.getHost().equals(boundaryUri.getHost())) {
      return false;
    }

    // Location path must start with boundary path
    String locationPath = locationUri.getPath() != null ? locationUri.getPath() : "";
    String boundaryPath = boundaryUri.getPath() != null ? boundaryUri.getPath() : "";

    // Normalize paths to handle trailing slashes
    if (!boundaryPath.endsWith("/") && !boundaryPath.isEmpty()) {
      boundaryPath = boundaryPath + "/";
    }
    if (!locationPath.endsWith("/") && !locationPath.isEmpty()) {
      locationPath = locationPath + "/";
    }

    return locationPath.startsWith(boundaryPath);
  }

  /**
   * Generates a managed table path under a schema's storage location.
   *
   * <p>Example: generateManagedTablePath("s3://bucket/schema/", "orders") returns
   * "s3://bucket/schema/orders/"
   *
   * @param schemaStorageLocation Base path (e.g., s3://bucket/schema/)
   * @param tableName Table name
   * @return Generated path (e.g., s3://bucket/schema/table_name/)
   * @throws BaseException if schema storage location is not configured
   */
  public static String generateManagedTablePath(String schemaStorageLocation, String tableName) {
    if (schemaStorageLocation == null || schemaStorageLocation.isEmpty()) {
      throw new BaseException(
          ErrorCode.INVALID_ARGUMENT,
          "Schema storage_location must be configured for managed tables");
    }

    if (tableName == null || tableName.isEmpty()) {
      throw new BaseException(ErrorCode.INVALID_ARGUMENT, "Table name cannot be null or empty");
    }

    // Ensure schema location ends with /
    String baseLocation =
        schemaStorageLocation.endsWith("/") ? schemaStorageLocation : schemaStorageLocation + "/";

    return baseLocation + tableName + "/";
  }

  /**
   * Validates catalog storage root format.
   *
   * <p>Ensures the storage root is a valid S3 URI (s3:// or s3a://).
   *
   * @param storageRoot The storage root to validate
   * @throws BaseException if storage root is not a valid S3 URI
   */
  public static void validateStorageRoot(String storageRoot) {
    if (storageRoot == null || storageRoot.isEmpty()) {
      return; // Optional field
    }

    URI uri;
    try {
      uri = URI.create(storageRoot);
    } catch (IllegalArgumentException e) {
      throw new BaseException(
          ErrorCode.INVALID_ARGUMENT, "Storage root is not a valid URI: " + storageRoot);
    }

    String scheme = uri.getScheme();
    if (scheme == null) {
      throw new BaseException(
          ErrorCode.INVALID_ARGUMENT,
          "Storage root must include a scheme (e.g., s3://): " + storageRoot);
    }

    if (!"s3".equals(scheme) && !"s3a".equals(scheme)) {
      throw new BaseException(
          ErrorCode.INVALID_ARGUMENT,
          "Storage root must be an s3:// or s3a:// URI, got: " + scheme + "://");
    }

    if (uri.getHost() == null || uri.getHost().isEmpty()) {
      throw new BaseException(
          ErrorCode.INVALID_ARGUMENT, "Storage root must include a bucket name: " + storageRoot);
    }
  }

  /**
   * Validates that a schema storage location is under the catalog's storage root.
   *
   * <p>This enforces the storage hierarchy: catalog root → schema location → table location
   *
   * @param catalogRoot The catalog's storage root (e.g., s3://bucket/)
   * @param schemaLocation The schema's storage location (e.g., s3://bucket/schema/)
   * @throws BaseException if schema location is not under catalog root
   */
  public static void validateSchemaLocation(String catalogRoot, String schemaLocation) {
    if (schemaLocation == null || schemaLocation.isEmpty()) {
      return; // Optional field
    }
    if (catalogRoot == null || catalogRoot.isEmpty()) {
      return; // No catalog root to validate against
    }

    if (!isWithinBoundary(schemaLocation, catalogRoot)) {
      throw new BaseException(
          ErrorCode.INVALID_ARGUMENT,
          "Schema storage_location must be under catalog storage_root. "
              + "Schema location: "
              + schemaLocation
              + ", Catalog root: "
              + catalogRoot);
    }
  }
}
