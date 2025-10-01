package io.unitycatalog.server.service;

import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.server.annotation.ExceptionHandler;
import com.linecorp.armeria.server.annotation.Post;
import io.unitycatalog.server.auth.UnityCatalogAuthorizer;
import io.unitycatalog.server.auth.decorator.KeyMapper;
import io.unitycatalog.server.auth.decorator.UnityAccessEvaluator;
import io.unitycatalog.server.exception.BaseException;
import io.unitycatalog.server.exception.ErrorCode;
import io.unitycatalog.server.exception.GlobalExceptionHandler;
import io.unitycatalog.server.model.*;
import io.unitycatalog.server.persist.Repositories;
import io.unitycatalog.server.persist.SchemaRepository;
import io.unitycatalog.server.persist.UserRepository;
import io.unitycatalog.server.service.credential.CloudCredentialVendor;
import io.unitycatalog.server.service.credential.CredentialContext;
import io.unitycatalog.server.utils.StorageLocationValidator;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static io.unitycatalog.server.model.SecurableType.CATALOG;
import static io.unitycatalog.server.model.SecurableType.METASTORE;
import static io.unitycatalog.server.model.SecurableType.SCHEMA;
import static io.unitycatalog.server.service.credential.CredentialContext.Privilege.SELECT;
import static io.unitycatalog.server.service.credential.CredentialContext.Privilege.UPDATE;

/**
 * Service for vending temporary credentials for table creation.
 *
 * <p>This service allows non-admin users with CREATE_TABLE privilege to obtain temporary S3
 * credentials for creating tables. The requested storage location is validated against the
 * schema's storage boundary to ensure multi-tenant security.
 */
@ExceptionHandler(GlobalExceptionHandler.class)
public class TemporaryTableCreationCredentialsService {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(TemporaryTableCreationCredentialsService.class);

  private final SchemaRepository schemaRepository;
  private final UserRepository userRepository;
  private final UnityAccessEvaluator evaluator;
  private final CloudCredentialVendor cloudCredentialVendor;
  private final KeyMapper keyMapper;

  @SneakyThrows
  public TemporaryTableCreationCredentialsService(
      UnityCatalogAuthorizer authorizer,
      CloudCredentialVendor cloudCredentialVendor,
      Repositories repositories) {
    this.evaluator = new UnityAccessEvaluator(authorizer);
    this.cloudCredentialVendor = cloudCredentialVendor;
    this.keyMapper = new KeyMapper(repositories);
    this.schemaRepository = repositories.getSchemaRepository();
    this.userRepository = repositories.getUserRepository();
  }

  @Post("")
  public HttpResponse generateTemporaryTableCredential(
      GenerateTemporaryTableCredential generateTemporaryTableCredential) {

    // Handle both new table creation flow and existing table flow
    String catalogName = generateTemporaryTableCredential.getCatalogName();
    String schemaName = generateTemporaryTableCredential.getSchemaName();
    String tableName = generateTemporaryTableCredential.getTableName();
    String requestedLocation = generateTemporaryTableCredential.getUrl();

    // If table_id is provided, fall back to existing flow (not implemented here yet)
    if (generateTemporaryTableCredential.getTableId() != null) {
      throw new BaseException(
          ErrorCode.INVALID_ARGUMENT,
          "table_id-based flow not yet supported in this endpoint. "
              + "Please use catalog_name/schema_name/table_name.");
    }

    // Validate required fields for table creation flow
    if (catalogName == null || schemaName == null || tableName == null) {
      throw new BaseException(
          ErrorCode.INVALID_ARGUMENT,
          "catalog_name, schema_name, and table_name are required for table creation");
    }

    LOGGER.info(
        "Vending table creation credentials for {}.{}.{}, requested location: {}",
        catalogName,
        schemaName,
        tableName,
        requestedLocation);

    // Authorize: CREATE_TABLE + USE_SCHEMA + USE_CATALOG
    authorizeForTableCreation(catalogName, schemaName);

    // Get schema storage location
    String schemaFullName = catalogName + "." + schemaName;
    SchemaInfo schema = schemaRepository.getSchema(schemaFullName);
    String schemaStorageLocation = schema.getStorageLocation();

    if (schemaStorageLocation == null || schemaStorageLocation.isEmpty()) {
      throw new BaseException(
          ErrorCode.INVALID_ARGUMENT,
          "Schema "
              + catalogName
              + "."
              + schemaName
              + " does not have storage_location configured. "
              + "Cannot create tables in this schema.");
    }

    // Determine final storage location
    String storageLocation;
    if (requestedLocation != null && !requestedLocation.isEmpty()) {
      // External table: validate location is within schema boundary
      if (!StorageLocationValidator.isWithinBoundary(requestedLocation, schemaStorageLocation)) {
        throw new BaseException(
            ErrorCode.PERMISSION_DENIED,
            "Requested storage location must be within schema boundary. "
                + "Requested: "
                + requestedLocation
                + ", Schema boundary: "
                + schemaStorageLocation);
      }
      storageLocation = requestedLocation;
    } else {
      // Managed table: generate location under schema
      storageLocation =
          StorageLocationValidator.generateManagedTablePath(schemaStorageLocation, tableName);
    }

    LOGGER.info("Validated storage location: {}", storageLocation);

    // Vend credentials with READ + WRITE privileges
    Set<CredentialContext.Privilege> privileges = Set.of(SELECT, UPDATE);
    TemporaryCredentials credentials =
        cloudCredentialVendor.vendCredential(storageLocation, privileges);

    // Add the validated URL to the response
    credentials.setUrl(storageLocation);

    LOGGER.info(
        "Successfully vended credentials for table creation: {}.{}.{}",
        catalogName,
        schemaName,
        tableName);

    return HttpResponse.ofJson(credentials);
  }

  private void authorizeForTableCreation(String catalogName, String schemaName) {
    // Authorization expression: CREATE_TABLE + USE_SCHEMA + USE_CATALOG
    String authorizeExpression =
        """
        #authorize(#principal, #schema, CREATE_TABLE) &&
        #authorize(#principal, #schema, USE_SCHEMA) &&
        #authorize(#principal, #catalog, USE_CATALOG)
        """;

    // KeyMapper builds full schema name from catalog + schema when both are provided
    Map<SecurableType, Object> resourceKeys =
        keyMapper.mapResourceKeys(
            Map.of(
                METASTORE, "metastore",
                CATALOG, catalogName,
                SCHEMA, schemaName));

    if (!evaluator.evaluate(userRepository.findPrincipalId(), authorizeExpression, resourceKeys)) {
      throw new BaseException(ErrorCode.PERMISSION_DENIED, "Access denied for table creation.");
    }
  }
}
