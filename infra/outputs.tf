output "api_gateway_url" {
  description = "API Gateway HTTP v2 invoke URL."
  value       = trimsuffix(aws_apigatewayv2_stage.default.invoke_url, "/")
}

output "api_gateway_api_id" {
  description = "API Gateway HTTP v2 API ID (used by the deploy workflow to tighten CORS post-apply)."
  value       = aws_apigatewayv2_api.sudoku.id
}

output "amplify_app_url" {
  description = "Amplify production branch URL."
  value       = "https://${aws_amplify_branch.main.branch_name}.${aws_amplify_app.sudoku.default_domain}"
}

output "lambda_function_name" {
  description = "Lambda function name."
  value       = aws_lambda_function.sudoku.function_name
}

output "lambda_function_arn" {
  description = "Lambda function ARN."
  value       = aws_lambda_function.sudoku.arn
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
  value       = "${local.cognito_domain}.auth.eu-west-2.amazoncognito.com"
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
  value       = aws_lambda_function.image_recognition.function_name
}

output "image_recognition_ecr_repository_url" {
  description = "ECR repository URL for the image recognition container image."
  value       = data.aws_ecr_repository.image_recognition.repository_url
}
