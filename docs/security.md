# Security & Cost Protection Guidelines

## Cloud & IAM Security

- **Least Privilege:** The AWS Lambda execution role must only have permissions for `logs:CreateLogStream` and `logs:PutLogEvents`. If DynamoDB is added later, grant access *only* to that specific table ARN.
- **No Hardcoded Secrets:** Never commit AWS credentials, API keys, or database URIs. Rely on local AWS profiles for Terraform execution and IAM roles/OIDC for CI/CD.

## Bill Protection

- **Throttling:** Implement strict rate limiting and burst limits on the API Gateway routes. This is critical to prevent runaway client-side loops or malicious actors from generating massive Lambda bills.
- **Timeouts:** Set a strict, low timeout on the Lambda function (e.g., 5 seconds). Sudoku generation should be instant; if it hangs, it should fail fast to save compute costs.

## Application Security

- **Input Validation:** Never trust client input. The backend must independently validate all Sudoku moves or submitted boards before processing them to prevent injection or logic abuse.
