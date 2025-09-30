# AwsCredentials
## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
| **access\_key\_id** | **String** | The access key ID that identifies the temporary credentials. | [optional] [default to null] |
| **secret\_access\_key** | **String** | The secret access key that can be used to sign AWS API requests. | [optional] [default to null] |
| **session\_token** | **String** | The token that users must pass to AWS API to use the temporary credentials. | [optional] [default to null] |
| **s3\_service\_endpoint** | **String** | Optional S3 service endpoint URL for S3-compatible storage (e.g., http://minio:9000). When not specified, the default AWS S3 endpoint is used. | [optional] [default to null] |
| **path\_style\_access** | **Boolean** | Whether to use path-style access for S3 requests. Required for MinIO and other S3-compatible storage systems. Defaults to false for AWS S3. | [optional] [default to null] |

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)

