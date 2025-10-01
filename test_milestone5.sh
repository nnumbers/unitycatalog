#!/bin/bash
set -e

echo "=== Milestone 5 Test: TemporaryTableCreationCredentialsService ==="
echo ""

BASE_URL="http://localhost:8080/api/2.1/unity-catalog"

echo "Test 1: Request credentials for managed table (no url provided)"
echo "Expected: Success with auto-generated storage location"
curl -s -X POST "${BASE_URL}/temporary-table-credentials" \
  -H "Content-Type: application/json" \
  -d '{
    "catalog_name": "unity",
    "schema_name": "default",
    "table_name": "my_new_table"
  }' | python3 -m json.tool
echo ""
echo "---"
echo ""

echo "Test 2: Request credentials for external table with valid location (within schema boundary)"
echo "Expected: Success with validated storage location"
curl -s -X POST "${BASE_URL}/temporary-table-credentials" \
  -H "Content-Type: application/json" \
  -d '{
    "catalog_name": "unity",
    "schema_name": "default",
    "table_name": "my_external_table",
    "url": "s3://test-bucket/unity/default/my_external_table/"
  }' | python3 -m json.tool
echo ""
echo "---"
echo ""

echo "Test 3: Request credentials for external table with invalid location (outside schema boundary)"
echo "Expected: 403 Permission Denied"
curl -s -X POST "${BASE_URL}/temporary-table-credentials" \
  -H "Content-Type: application/json" \
  -d '{
    "catalog_name": "unity",
    "schema_name": "default",
    "table_name": "my_table",
    "url": "s3://malicious-bucket/data/"
  }' | python3 -m json.tool
echo ""
echo "---"
echo ""

echo "Test 4: Request with missing required fields"
echo "Expected: 400 Invalid Argument"
curl -s -X POST "${BASE_URL}/temporary-table-credentials" \
  -H "Content-Type: application/json" \
  -d '{
    "catalog_name": "unity"
  }' | python3 -m json.tool
echo ""
echo "---"
echo ""

echo "Test 5: Request with table_id (old flow)"
echo "Expected: 400 Invalid Argument (not yet supported)"
curl -s -X POST "${BASE_URL}/temporary-table-credentials" \
  -H "Content-Type: application/json" \
  -d '{
    "table_id": "some-table-uuid"
  }' | python3 -m json.tool
echo ""
echo "---"
echo ""

echo "=== Tests Complete ==="
