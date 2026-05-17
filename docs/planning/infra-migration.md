# Directive: Refactor Bespoke Terraform to High-Level Modules

## Goal

Convert custom, low-level AWS resource declarations for a Serverless SPA + API Gateway stack into verified community modules (`cloudposse` and `terraform-aws-modules`) with zero resource destruction.

## Rules of Engagement

1. Do NOT delete existing state. Every infrastructure swap must use native Terraform `moved {}` blocks to preserve existing cloud resources - we should test this migration with an `rc` branch before attempting `main`.
2. Maintain existing feature parity: CloudFront must continue to route 404s to `index.html`, and CORS origins must remain intact on the API Gateway.
3. Use `terraform plan` after every block refactor to verify that the plan output shows `moved` actions and zero `destroy` actions for core data/routing components.

## Execution Steps

### Phase 1: Frontend State Preservation

1. Map the bespoke `aws_s3_bucket`, `aws_cloudfront_distribution`, and Route53 records into the `cloudposse/cloudfront-s3-cdn/aws` module structure.
2. Write corresponding `moved {}` blocks in a new `migrations.tf` file.
3. Run `terraform plan` to validate the state migration for the frontend layer.

### Phase 2: API Gateway & Lambda Refactor

1. Replace bespoke Lambda IAM roles, log groups, and functions with `terraform-aws-modules/lambda/aws`.
2. Replace bespoke API Gateway routes, integrations, and stages with `terraform-aws-modules/apigateway-v2/aws`.
3. Add `moved {}` blocks for the functions and API gateway components where applicable. If the bespoke API Gateway setup is too fragmented to map cleanly via `moved` blocks, flag it for manual review before proceeding.

### Phase 3: Cleanup & Validation

1. Remove the old bespoke code blocks once the `moved` paths are verified.
2. Run a final `terraform plan` to ensure a completely clean output ("No changes. Your infrastructure matches the configuration.").