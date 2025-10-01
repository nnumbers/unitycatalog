package io.unitycatalog.server.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.unitycatalog.server.exception.BaseException;
import org.junit.jupiter.api.Test;

public class StorageLocationValidatorTest {

  // ==================== isWithinBoundary Tests ====================

  @Test
  public void testIsWithinBoundary_SameBucket_SamePrefix() {
    // Location under boundary - should be valid
    assertThat(
            StorageLocationValidator.isWithinBoundary(
                "s3://bucket/schema/table/", "s3://bucket/schema/"))
        .isTrue();
  }

  @Test
  public void testIsWithinBoundary_SameBucket_DifferentPrefix() {
    // Location NOT under boundary - different path
    assertThat(
            StorageLocationValidator.isWithinBoundary(
                "s3://bucket/other/table/", "s3://bucket/schema/"))
        .isFalse();
  }

  @Test
  public void testIsWithinBoundary_DifferentBucket() {
    // Different bucket - should be invalid
    assertThat(
            StorageLocationValidator.isWithinBoundary(
                "s3://other-bucket/schema/table/", "s3://bucket/schema/"))
        .isFalse();
  }

  @Test
  public void testIsWithinBoundary_DifferentScheme() {
    // Different scheme (s3a vs s3) - should be invalid
    assertThat(
            StorageLocationValidator.isWithinBoundary(
                "s3a://bucket/schema/table/", "s3://bucket/schema/"))
        .isFalse();
  }

  @Test
  public void testIsWithinBoundary_SameScheme_S3A() {
    // Both using s3a:// - should be valid
    assertThat(
            StorageLocationValidator.isWithinBoundary(
                "s3a://bucket/schema/table/", "s3a://bucket/schema/"))
        .isTrue();
  }

  @Test
  public void testIsWithinBoundary_WithoutTrailingSlash() {
    // Boundary without trailing slash - should still work
    assertThat(
            StorageLocationValidator.isWithinBoundary(
                "s3://bucket/schema/table/", "s3://bucket/schema"))
        .isTrue();
  }

  @Test
  public void testIsWithinBoundary_LocationWithoutTrailingSlash() {
    // Location without trailing slash - should still work
    assertThat(
            StorageLocationValidator.isWithinBoundary(
                "s3://bucket/schema/table", "s3://bucket/schema/"))
        .isTrue();
  }

  @Test
  public void testIsWithinBoundary_BothWithoutTrailingSlash() {
    // Both without trailing slash - should still work
    assertThat(
            StorageLocationValidator.isWithinBoundary(
                "s3://bucket/schema/table", "s3://bucket/schema"))
        .isTrue();
  }

  @Test
  public void testIsWithinBoundary_NullBoundary() {
    // Null boundary - should deny by default
    assertThat(StorageLocationValidator.isWithinBoundary("s3://bucket/schema/table/", null))
        .isFalse();
  }

  @Test
  public void testIsWithinBoundary_EmptyBoundary() {
    // Empty boundary - should deny by default
    assertThat(StorageLocationValidator.isWithinBoundary("s3://bucket/schema/table/", ""))
        .isFalse();
  }

  @Test
  public void testIsWithinBoundary_NullLocation() {
    // Null location - should be invalid
    assertThat(StorageLocationValidator.isWithinBoundary(null, "s3://bucket/schema/")).isFalse();
  }

  @Test
  public void testIsWithinBoundary_EmptyLocation() {
    // Empty location - should be invalid
    assertThat(StorageLocationValidator.isWithinBoundary("", "s3://bucket/schema/")).isFalse();
  }

  @Test
  public void testIsWithinBoundary_InvalidLocationURI() {
    // Invalid URI - should be invalid
    assertThat(StorageLocationValidator.isWithinBoundary("not a valid uri", "s3://bucket/schema/"))
        .isFalse();
  }

  @Test
  public void testIsWithinBoundary_InvalidBoundaryURI() {
    // Invalid boundary URI - should be invalid
    assertThat(
            StorageLocationValidator.isWithinBoundary(
                "s3://bucket/schema/table/", "not a valid uri"))
        .isFalse();
  }

  @Test
  public void testIsWithinBoundary_DeepNesting() {
    // Deep path nesting - should work
    assertThat(
            StorageLocationValidator.isWithinBoundary(
                "s3://bucket/schema/subfolder/another/table/", "s3://bucket/schema/"))
        .isTrue();
  }

  @Test
  public void testIsWithinBoundary_BucketRootBoundary() {
    // Boundary is bucket root - should allow anything under bucket
    assertThat(
            StorageLocationValidator.isWithinBoundary("s3://bucket/schema/table/", "s3://bucket/"))
        .isTrue();
  }

  @Test
  public void testIsWithinBoundary_PrefixMatch_NotExactPath() {
    // "s3://bucket/schema2/" should NOT match boundary "s3://bucket/schema/"
    // Even though "schema2" starts with "schema"
    assertThat(
            StorageLocationValidator.isWithinBoundary(
                "s3://bucket/schema2/table/", "s3://bucket/schema/"))
        .isFalse();
  }

  // ==================== generateManagedTablePath Tests ====================

  @Test
  public void testGenerateManagedTablePath_WithTrailingSlash() {
    String result =
        StorageLocationValidator.generateManagedTablePath("s3://bucket/schema/", "orders");
    assertThat(result).isEqualTo("s3://bucket/schema/orders/");
  }

  @Test
  public void testGenerateManagedTablePath_WithoutTrailingSlash() {
    String result =
        StorageLocationValidator.generateManagedTablePath("s3://bucket/schema", "orders");
    assertThat(result).isEqualTo("s3://bucket/schema/orders/");
  }

  @Test
  public void testGenerateManagedTablePath_BucketRoot() {
    String result = StorageLocationValidator.generateManagedTablePath("s3://bucket/", "orders");
    assertThat(result).isEqualTo("s3://bucket/orders/");
  }

  @Test
  public void testGenerateManagedTablePath_NullSchemaLocation() {
    assertThatThrownBy(() -> StorageLocationValidator.generateManagedTablePath(null, "orders"))
        .isInstanceOf(BaseException.class)
        .hasMessageContaining("Schema storage_location must be configured");
  }

  @Test
  public void testGenerateManagedTablePath_EmptySchemaLocation() {
    assertThatThrownBy(() -> StorageLocationValidator.generateManagedTablePath("", "orders"))
        .isInstanceOf(BaseException.class)
        .hasMessageContaining("Schema storage_location must be configured");
  }

  @Test
  public void testGenerateManagedTablePath_NullTableName() {
    assertThatThrownBy(
            () -> StorageLocationValidator.generateManagedTablePath("s3://bucket/schema/", null))
        .isInstanceOf(BaseException.class)
        .hasMessageContaining("Table name cannot be null or empty");
  }

  @Test
  public void testGenerateManagedTablePath_EmptyTableName() {
    assertThatThrownBy(
            () -> StorageLocationValidator.generateManagedTablePath("s3://bucket/schema/", ""))
        .isInstanceOf(BaseException.class)
        .hasMessageContaining("Table name cannot be null or empty");
  }

  // ==================== validateStorageRoot Tests ====================

  @Test
  public void testValidateStorageRoot_ValidS3() {
    // Valid s3:// URI - should not throw
    StorageLocationValidator.validateStorageRoot("s3://my-bucket/");
  }

  @Test
  public void testValidateStorageRoot_ValidS3A() {
    // Valid s3a:// URI - should not throw
    StorageLocationValidator.validateStorageRoot("s3a://my-bucket/");
  }

  @Test
  public void testValidateStorageRoot_ValidS3_WithPrefix() {
    // Valid s3:// URI with path prefix - should not throw
    StorageLocationValidator.validateStorageRoot("s3://my-bucket/prefix/");
  }

  @Test
  public void testValidateStorageRoot_Null() {
    // Null is allowed (optional field) - should not throw
    StorageLocationValidator.validateStorageRoot(null);
  }

  @Test
  public void testValidateStorageRoot_Empty() {
    // Empty is allowed (optional field) - should not throw
    StorageLocationValidator.validateStorageRoot("");
  }

  @Test
  public void testValidateStorageRoot_InvalidScheme_GCS() {
    assertThatThrownBy(() -> StorageLocationValidator.validateStorageRoot("gs://bucket/"))
        .isInstanceOf(BaseException.class)
        .hasMessageContaining("must be an s3:// or s3a:// URI");
  }

  @Test
  public void testValidateStorageRoot_InvalidScheme_HDFS() {
    assertThatThrownBy(() -> StorageLocationValidator.validateStorageRoot("hdfs://namenode/"))
        .isInstanceOf(BaseException.class)
        .hasMessageContaining("must be an s3:// or s3a:// URI");
  }

  @Test
  public void testValidateStorageRoot_NoScheme() {
    assertThatThrownBy(() -> StorageLocationValidator.validateStorageRoot("my-bucket/path/"))
        .isInstanceOf(BaseException.class)
        .hasMessageContaining("must include a scheme");
  }

  @Test
  public void testValidateStorageRoot_InvalidURI() {
    assertThatThrownBy(() -> StorageLocationValidator.validateStorageRoot("not a valid uri!!!"))
        .isInstanceOf(BaseException.class)
        .hasMessageContaining("not a valid URI");
  }

  @Test
  public void testValidateStorageRoot_NoBucket() {
    assertThatThrownBy(() -> StorageLocationValidator.validateStorageRoot("s3:///path/"))
        .isInstanceOf(BaseException.class)
        .hasMessageContaining("must include a bucket name");
  }

  // ==================== validateSchemaLocation Tests ====================

  @Test
  public void testValidateSchemaLocation_ValidUnderCatalog() {
    // Schema location under catalog root - should not throw
    StorageLocationValidator.validateSchemaLocation("s3://bucket/", "s3://bucket/schema/");
  }

  @Test
  public void testValidateSchemaLocation_ValidDeepNesting() {
    // Schema location with deep nesting under catalog - should not throw
    StorageLocationValidator.validateSchemaLocation(
        "s3://bucket/catalog/", "s3://bucket/catalog/schema/");
  }

  @Test
  public void testValidateSchemaLocation_NullSchemaLocation() {
    // Null schema location (optional) - should not throw
    StorageLocationValidator.validateSchemaLocation("s3://bucket/", null);
  }

  @Test
  public void testValidateSchemaLocation_EmptySchemaLocation() {
    // Empty schema location (optional) - should not throw
    StorageLocationValidator.validateSchemaLocation("s3://bucket/", "");
  }

  @Test
  public void testValidateSchemaLocation_NullCatalogRoot() {
    // Null catalog root (no validation needed) - should not throw
    StorageLocationValidator.validateSchemaLocation(null, "s3://bucket/schema/");
  }

  @Test
  public void testValidateSchemaLocation_EmptyCatalogRoot() {
    // Empty catalog root (no validation needed) - should not throw
    StorageLocationValidator.validateSchemaLocation("", "s3://bucket/schema/");
  }

  @Test
  public void testValidateSchemaLocation_DifferentBucket() {
    assertThatThrownBy(
            () ->
                StorageLocationValidator.validateSchemaLocation(
                    "s3://bucket/", "s3://other-bucket/schema/"))
        .isInstanceOf(BaseException.class)
        .hasMessageContaining("Schema storage_location must be under catalog storage_root");
  }

  @Test
  public void testValidateSchemaLocation_WrongPrefix() {
    assertThatThrownBy(
            () ->
                StorageLocationValidator.validateSchemaLocation(
                    "s3://bucket/catalog1/", "s3://bucket/catalog2/schema/"))
        .isInstanceOf(BaseException.class)
        .hasMessageContaining("Schema storage_location must be under catalog storage_root");
  }
}
