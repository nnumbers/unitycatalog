# Unity Catalog Server Configuration

## Configuration Files

- **`server.properties`** - Default configuration with examples (tracked in git)
- **`server.properties.local`** - Local overrides with secrets (gitignored)
- **`server.properties.local.template`** - Template for local configuration

## Quick Setup

### 1. Create Local Configuration File

```bash
cd etc/conf
cp server.properties.local.template server.properties.local
```

### 2. Edit Your Secrets

Edit `server.properties.local` with your actual credentials:

```properties
# For MinIO
s3.bucketPath.0=s3://my-bucket
s3.region.0=us-east-1
s3.s3ServiceEndpoint.0=http://localhost:9000
s3.stsEndpoint.0=http://localhost:9000
s3.pathStyleAccess.0=true
s3.accessKey.0=YOUR_ACCESS_KEY
s3.secretKey.0=YOUR_SECRET_KEY
```

### 3. Copy to Active Configuration

```bash
cp server.properties.local server.properties
```

### 4. Start the Server

```bash
bin/start-uc-server
```

### 5. Before Committing Changes

Reset `server.properties` to avoid committing secrets:

```bash
git checkout server.properties
```

**Tip**: Create a git alias for convenience:
```bash
git config alias.reset-props '!git checkout etc/conf/server.properties'
```

Then use: `git reset-props` before committing.

## S3 Multi-Endpoint Configuration

Unity Catalog supports multiple S3-compatible storage endpoints simultaneously:

### AWS S3
```properties
s3.bucketPath.0=s3://my-aws-bucket
s3.region.0=us-east-1
s3.awsRoleArn.0=arn:aws:iam::123456789012:role/my-role
```

### MinIO
```properties
s3.bucketPath.1=s3://my-minio-bucket
s3.region.1=us-east-1
s3.s3ServiceEndpoint.1=http://minio:9000
s3.stsEndpoint.1=http://minio:9000
s3.pathStyleAccess.1=true
s3.accessKey.1=minioadmin
s3.secretKey.1=minioadmin
```

### Ceph / Dell ECS / Other S3-Compatible
```properties
s3.bucketPath.2=s3://my-ceph-bucket
s3.region.2=us-east-1
s3.s3ServiceEndpoint.2=https://ceph.example.com
s3.stsEndpoint.2=https://ceph.example.com
s3.pathStyleAccess.2=true
s3.accessKey.2=YOUR_ACCESS_KEY
s3.secretKey.2=YOUR_SECRET_KEY
```

## Configuration Parameters

### S3 Storage Configuration

| Parameter | Required | Description | Example |
|-----------|----------|-------------|---------|
| `s3.bucketPath.N` | Yes | S3 bucket path | `s3://my-bucket` |
| `s3.region.N` | Yes | AWS region | `us-east-1` |
| `s3.awsRoleArn.N` | No | IAM role ARN for STS AssumeRole | `arn:aws:iam::123456789012:role/my-role` |
| `s3.s3ServiceEndpoint.N` | No | Custom S3 endpoint URL | `http://minio:9000` |
| `s3.stsEndpoint.N` | No | Custom STS endpoint URL | `http://minio:9000` |
| `s3.pathStyleAccess.N` | No | Use path-style access (default: true) | `true` or `false` |
| `s3.accessKey.N` | No | Access key (uses DefaultCredentialsProviderChain if blank) | `AKIAIOSFODNN7EXAMPLE` |
| `s3.secretKey.N` | No | Secret key | `wJalrXUtnFEMI/K7MDENG/bPxRfiCY` |
| `s3.sessionToken.N` | No | Session token (test only - skips STS if provided) | |

**Note**: Replace `N` with an incrementing index (0, 1, 2, ...) to configure multiple storage backends.

## Security Best Practices

1. **Never commit secrets to git**
   - Use `server.properties.local` for all credentials
   - Verify it's in `.gitignore`

2. **Use IAM roles when possible**
   - On AWS EC2/ECS, leave `accessKey`/`secretKey` blank
   - Configure `awsRoleArn` for STS AssumeRole

3. **Rotate credentials regularly**
   - Update `server.properties.local` with new credentials
   - Restart the server to apply changes

4. **Restrict permissions**
   - Grant minimum required S3 permissions
   - Use IAM policies to scope access by prefix

## Loading Order

The server loads configuration in this order:

1. `server.properties` - Default configuration
2. `server.properties.local` - Local overrides (if exists)
3. System environment variables (if any)

Settings in later files override earlier ones.

## Verification

After starting the server, verify your S3 configuration:

```bash
curl http://localhost:8080/api/2.1/unity-catalog/s3-bucket-configurations | jq
```

Expected output:
```json
{
  "configurations": [
    {
      "bucket_name": "my-bucket",
      "endpoint": "http://minio:9000",
      "region": "us-east-1",
      "path_style_access": true
    }
  ]
}
```

## Troubleshooting

### Server won't start
- Check for syntax errors in `server.properties.local`
- Verify all required fields are filled in
- Check server logs: `etc/logs/server.log`

### S3 connection errors
- Verify endpoint URL is accessible from server
- Check credentials are correct
- Ensure `pathStyleAccess=true` for MinIO/Ceph

### Credentials not working
- For AWS, verify IAM role has correct permissions
- For MinIO, verify access key has required policies
- Check STS endpoint is correct and accessible

## Related Documentation

- [S3 Multi-Endpoint Configuration Guide](../../docs/usage/s3-multi-endpoint-configuration.md)
- [Internal Artifactory Deployment](../../docs/deployment/internal-artifactory-deployment.md)