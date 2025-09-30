# S3BucketConfiguration
## Properties

| Name | Type | Description | Notes |
|------------ | ------------- | ------------- | -------------|
| **bucket\_name** | **String** | The name of the S3 bucket (e.g., \&quot;my-bucket\&quot; from \&quot;s3://my-bucket/path\&quot;) | [default to null] |
| **endpoint** | **String** | S3 endpoint URL (e.g., \&quot;http://minio:9000\&quot;, \&quot;https://s3.us-west-2.amazonaws.com\&quot;) | [optional] [default to null] |
| **region** | **String** | AWS region for the bucket (e.g., \&quot;us-east-1\&quot;) | [optional] [default to null] |
| **path\_style\_access** | **Boolean** | Whether to use path-style access for S3 requests. Required for MinIO/Ceph. Defaults to false for AWS S3. | [optional] [default to null] |

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)

