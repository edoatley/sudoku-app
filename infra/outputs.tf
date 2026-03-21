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
  value       = aws_cognito_user_pool.main.id
}

output "cognito_client_id" {
  description = "Cognito App Client ID (public, safe to embed in frontend)."
  value       = aws_cognito_user_pool_client.web.id
}

output "cognito_domain" {
  description = "Cognito hosted UI domain (used by the deploy workflow to tighten callback URLs post-apply)."
  value       = "${aws_cognito_user_pool_domain.main.domain}.auth.eu-west-2.amazoncognito.com"
}
