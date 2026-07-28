output "api_gateway_url" {
  description = "API Gateway HTTP v2 invoke URL."
  value       = module.api_gateway.api_endpoint
}

output "api_gateway_api_id" {
  description = "API Gateway HTTP v2 API ID. CORS is now fully Terraform-managed; this output is unused by ci-deploy.yml (feeds an --api-id flag amplify-post-deploy.sh explicitly ignores for backwards-compat) but is still used by deploy-local.sh's own (now redundant) CORS-tightening step."
  value       = module.api_gateway.api_id
}

output "amplify_app_url" {
  description = "Primary URL for the Amplify app (custom domain when available)."
  value = local.is_default ? "https://sudoku.edoatley.co.uk" : (
    local.is_rc ? "https://sudoku-beta.edoatley.co.uk" :
    "https://${aws_amplify_branch.main.branch_name}.${aws_amplify_app.sudoku.default_domain}"
  )
}

output "amplify_default_url" {
  description = "Raw Amplify branch URL (used for readiness probes before custom domain DNS propagates)."
  value       = "https://${aws_amplify_branch.main.branch_name}.${aws_amplify_app.sudoku.default_domain}"
}

output "subdomain_nameservers" {
  description = "Name servers for the sudoku.edoatley.co.uk hosted zone. Copy these into infra/scripts/delegate-dns.sh."
  value       = local.is_default ? aws_route53_zone.sudoku[0].name_servers : []
}

output "route53_zone_id" {
  description = "Route53 hosted zone ID for sudoku.edoatley.co.uk (read by rc-* workspaces via remote state)."
  value       = local.is_default ? aws_route53_zone.sudoku[0].zone_id : null
}

output "sudoku_beta_nameservers" {
  description = "Name servers for the sudoku-beta.edoatley.co.uk hosted zone. Use these to fix the NS delegation in the parent account."
  value       = local.is_default ? aws_route53_zone.sudoku_beta[0].name_servers : []
}

output "route53_beta_zone_id" {
  description = "Route53 hosted zone ID for sudoku-beta.edoatley.co.uk (read by rc-* workspaces via remote state)."
  value       = local.is_default ? aws_route53_zone.sudoku_beta[0].zone_id : null
}

output "lambda_function_name" {
  description = "Lambda function name."
  value       = module.lambda.lambda_function_name
}

output "lambda_function_arn" {
  description = "Lambda function ARN."
  value       = module.lambda.lambda_function_arn
}

output "amplify_app_id" {
  description = "Amplify app ID (used by CI to trigger branch builds on first deploy)."
  value       = aws_amplify_app.sudoku.id
}

output "cognito_user_pool_id" {
  description = "Cognito User Pool ID."
  value       = local.cognito_user_pool_id
}

output "cognito_client_id" {
  description = "Cognito App Client ID (public, safe to embed in frontend)."
  value       = local.cognito_web_client_id
}

output "cognito_domain" {
  description = "Cognito hosted UI domain (used by the deploy workflow to tighten callback URLs post-apply)."
  value       = "${local.cognito_domain}.auth.${local.aws_region}.amazoncognito.com"
}

output "cognito_smoke_test_client_id" {
  description = "Smoke-test app client ID (server-side, used by CI only)."
  value       = local.cognito_smoke_client_id
}

output "cognito_smoke_test_client_secret" {
  description = "Smoke-test app client secret (server-side, used by CI only)."
  value       = local.cognito_smoke_client_secret
  sensitive   = true
}

output "image_recognition_lambda_function_name" {
  description = "Image recognition Lambda function name."
  value       = module.image_recognition_lambda.lambda_function_name
}

output "image_recognition_ecr_repository_url" {
  description = "ECR repository URL for the image recognition container image."
  value       = data.aws_ecr_repository.image_recognition.repository_url
}

output "backend_ecr_repository_url" {
  description = "ECR repository URL for the backend Lambda container image."
  value       = data.aws_ecr_repository.sudoku_backend.repository_url
}
