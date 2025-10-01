# Security Incident Report: AWS Credentials Exposed in Git History

## Incident Summary

**Date Discovered:** October 1, 2025
**Severity:** HIGH
**Status:** Mitigated (Local), Requires Remote Cleanup

## What Happened

AWS credentials were accidentally committed to the `etc/conf/server.properties` file and pushed to GitHub.

### Exposed Credentials

**Repository:** https://github.com/ray-harrison/unitycatalog
**Branch:** `feature/schema-storage-locations`
**Commits Containing Secrets:**
- `6a258d7` - Fix: Copy storage fields in CatalogRepository and SchemaRepository
- All subsequent commits inherited the secrets

**Exposed Information:**
```
s3.bucketPath.0=s3://dx.dl.comcast.dxarchitecturepoc
s3.region.0=us-east-1
s3.awsRoleArn.0=arn:aws:iam::123456789012:role/role-name
s3.s3ServiceEndpoint.0=https://opstoreprod.dxplatform.comcast.com
s3.stsEndpoint.0=https://opstoreprod.dxplatform.comcast.com
s3.pathStyleAccess.0=true
s3.accessKey.0=svc-dxarchpoc-dev-prod
s3.secretKey.0=YKrNr5XM9o5DmaVSyqVjUMPbItahcRmijBd
```

## Immediate Actions Taken

### 1. ✅ Local Repository Cleaned
- Removed secrets from `etc/conf/server.properties`
- Committed clean version: `6c9f5ac SECURITY: Remove secrets from server.properties`
- Used `git filter-branch` to rewrite history for commits `e022bba..HEAD`
- Cleaned up original refs
- Expired reflog and ran garbage collection
- Dropped stash containing secrets

### 2. ✅ History Rewritten
**Old commit hashes (CONTAIN SECRETS):**
- `6a7f8ea` → `5b5d346` (Milestone 4)
- `1c1d1b1` → `bac194b` (Milestone 3)
- `6a258d7` → `2094aa5` (Fix: Copy storage fields) **← ORIGINAL COMMIT WITH SECRETS**

**New commit hashes (SECRETS REMOVED):**
- `5b5d346` - Milestone 4: Add table location validation to TableRepository
- `bac194b` - Milestone 3: Add StorageLocationValidator utility with comprehensive tests
- `2094aa5` - Fix: Copy storage fields in CatalogRepository and SchemaRepository
- `6c9f5ac` - SECURITY: Remove secrets from server.properties

### 3. ⚠️ Remote Branch Status
**Branch pushed to GitHub:** YES
**Public repository:** NO (private fork ray-harrison/unitycatalog)
**Upstream affected:** NO (not yet merged or pushed to unitycatalog/unitycatalog)

## Required Actions

### Immediate (Within 1 Hour)

1. **Rotate AWS Credentials**
   ```
   Access Key: svc-dxarchpoc-dev-prod
   Secret Key: YKrNr5XM9o5DmaVSyqVjUMPbItahcRmijBd
   ```
   - [ ] Disable/revoke these credentials in AWS IAM
   - [ ] Generate new credentials
   - [ ] Update local configuration (use `etc/conf/server.properties.local`)
   - [ ] Notify security team

2. **Force Push Cleaned History**
   ```bash
   cd /Users/RHarri005@cable.comcast.com/opensource/unitycatalog-ai/unitycatalog
   git push --force origin feature/schema-storage-locations
   ```
   - This will overwrite the remote branch with clean history
   - Old commits with secrets will no longer be accessible via normal git operations
   - GitHub may still have the commits in their internal storage for ~30 days

3. **Contact GitHub Support** (Optional but Recommended)
   - Request cache purge for commits: `6a258d7`, `6a7f8ea`, `1c1d1b1`
   - GitHub support: https://support.github.com/
   - Reference: https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository

### Short Term (Within 24 Hours)

4. **Audit for Impact**
   - [ ] Check AWS CloudTrail for unauthorized access using these credentials
   - [ ] Review S3 bucket access logs for `s3://dx.dl.comcast.dxarchitecturepoc`
   - [ ] Check if any unauthorized actions were taken

5. **Update Documentation**
   - [ ] Document incident in security log
   - [ ] Update credential management procedures
   - [ ] Add pre-commit hooks to prevent future credential leaks

### Long Term

6. **Implement Preventative Measures**
   - [ ] Set up git-secrets or similar tool: https://github.com/awslabs/git-secrets
   - [ ] Add pre-commit hook to scan for credentials
   - [ ] Use environment variables instead of config files for secrets
   - [ ] Add `etc/conf/*.properties.local` to .gitignore (already present)
   - [ ] Enforce AWS IAM role-based authentication (no static keys)

## Verification Steps

To verify the local repository is clean:

```bash
# Search for the exposed secret key
git log --all --source --full-history -S "YKrNr5XM9o5DmaVSyqVjUMPbItahcRmijBd"
# Should return: nothing (empty)

# Search for the exposed access key
git log --all --source --full-history -S "svc-dxarchpoc-dev-prod"
# Should return: nothing (empty)

# Check current branch
git log --oneline -5
# Should show: 6c9f5ac, 5b5d346, bac194b, 2094aa5, 6a86535 (NEW hashes)
```

## Lessons Learned

1. **Never commit credentials to git** - Use environment variables, AWS Parameter Store, or local config files excluded from git
2. **Review before pushing** - Always review `git diff` before committing/pushing
3. **Use proper patterns** - The codebase already had `.gitignore` entries for `etc/conf/server.properties.local` - should have used that
4. **Automated scanning** - Need pre-commit hooks to catch secrets before they're committed

## Contact Information

**Security Team:** [Your Security Contact]
**Incident Owner:** Ray Harrison (r.harris1@comcast.net)
**Date:** October 1, 2025

---

## Checklist

- [x] Secrets removed from local repository
- [x] Git history rewritten locally
- [ ] AWS credentials rotated
- [ ] Force push to GitHub completed
- [ ] GitHub support contacted for cache purge
- [ ] Impact audit completed
- [ ] Incident documented
- [ ] Preventative measures implemented
