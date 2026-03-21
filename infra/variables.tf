variable "github_token" {
  description = "GitHub classic OAuth token (repo scope) for Amplify repository connection."
  type        = string
  sensitive   = true
}

variable "github_owner" {
  description = "GitHub repository owner."
  type        = string
  default     = "edoatley"
}

variable "github_repo" {
  description = "GitHub repository name."
  type        = string
  default     = "sudoku-app"
}

variable "environment" {
  description = "Deployment environment label applied to all resources via provider default_tags."
  type        = string
  default     = "prod"
}

variable "lambda_zip_path" {
  description = "Local path to the Lambda deployment zip produced by the Maven build."
  type        = string
  default     = "../backend/target/function.zip"
}

variable "api_gateway_throttle_burst_limit" {
  description = "API Gateway stage throttle burst limit (requests)."
  type        = number
  default     = 50
}

variable "api_gateway_throttle_rate_limit" {
  description = "API Gateway stage throttle rate limit (requests per second)."
  type        = number
  default     = 25
}

variable "google_client_id" {
  description = "Google OAuth 2.0 Client ID for Cognito social login."
  type        = string
  sensitive   = true
}

variable "google_client_secret" {
  description = "Google OAuth 2.0 Client Secret for Cognito social login."
  type        = string
  sensitive   = true
}
