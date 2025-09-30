# CatalogInfo
## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
| **name** | **String** | Name of catalog. | [optional] [default to null] |
| **comment** | **String** | User-provided free-form text description. | [optional] [default to null] |
| **properties** | **Map** | A map of key-value properties attached to the securable. | [optional] [default to null] |
| **owner** | **String** | Username of current owner of catalog. | [optional] [default to null] |
| **storage\_root** | **String** | Optional S3 URI for managed table storage root. Example: s3://my-catalog-bucket/ or s3://my-catalog-bucket/prod/ Required for creating managed tables in this catalog.  | [optional] [default to null] |
| **created\_at** | **Long** | Time at which this catalog was created, in epoch milliseconds. | [optional] [default to null] |
| **created\_by** | **String** | Username of catalog creator. | [optional] [default to null] |
| **updated\_at** | **Long** | Time at which this catalog was last modified, in epoch milliseconds. | [optional] [default to null] |
| **updated\_by** | **String** | Username of user who last modified catalog. | [optional] [default to null] |
| **id** | **String** | Unique identifier for the catalog. | [optional] [default to null] |

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)

