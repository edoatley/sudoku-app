# Infrastructure Guidelines

**Stack:** Terraform, AWS

## Resource Definitions

- **File Structure:** Maintain a clean separation of concerns. Use `main.tf` (provider), `variables.tf` (inputs), `outputs.tf` (exports), `lambda.tf` (compute), `api_gateway.tf` (routing), `dynamodb.tf` (data), `amplify.tf` (frontend hosting), and `iam.tf` (roles and policies).
- **API Gateway:** Strictly use **API Gateway v2 (HTTP API)** for cost and performance efficiency. Do not use REST API v1.
- **CORS:** Use the two-step pattern to achieve tight CORS without a circular dependency:
  1. **Terraform-managed baseline** — set `allow_origins` to `["https://*.amplifyapp.com", "http://localhost:5173"]` in the `cors_configuration` block. This wildcard is intentionally broad to avoid the circular dependency where Amplify needs the API Gateway URL before it exists and vice versa.
  2. **Post-apply tightening** — immediately after `terraform apply`, the deploy workflow calls `aws apigatewayv2 update-api` to replace the wildcard with the exact Amplify URL (e.g. `https://main.abc123.amplifyapp.com`).
  3. Add `lifecycle { ignore_changes = [cors_configuration] }` to the `aws_apigatewayv2_api` resource so subsequent `terraform apply` runs do not revert the tightened origin.
- **Frontend Hosting:** Use AWS Amplify (`aws_amplify_app`, `aws_amplify_branch`) to host the React application directly from the GitHub repository. Always set `enable_auto_build = false` on `aws_amplify_branch` (and in `auto_branch_creation_config`). The CI workflow triggers builds explicitly via `aws amplify start-job` *after* `terraform apply` has set the correct environment variables (e.g. `VITE_API_URL`). If auto-build is left enabled, Amplify starts building on push before Terraform runs, baking a stale URL into the Vite bundle.
- **Tagging:** Apply a default set of tags to every resource via the provider `default_tags` block (`Project = "Sudoku"`, `ManagedBy = "Terraform"`, `Environment = var.environment`) to ensure the AWS bill is easily trackable.
