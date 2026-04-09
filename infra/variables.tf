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

variable "smoke_test_user_email" {
  description = "Email address of the CI smoke-test Cognito user. Never used for real logins."
  type        = string
  sensitive   = true
}

variable "smoke_test_user_password" {
  description = "Permanent password for the CI smoke-test Cognito user."
  type        = string
  sensitive   = true
}

variable "rc_cognito_web_client_id" {
  description = "Client ID of the shared sudoku-web-rc Cognito app client (rc-* workspaces only)."
  type        = string
  default     = ""
}

variable "rc_cognito_smoke_client_id" {
  description = "Client ID of the shared sudoku-smoke-test-rc Cognito app client (rc-* workspaces only)."
  type        = string
  default     = ""
}

variable "image_recognition_image_uri" {
  description = "ECR image URI for the image recognition Lambda (e.g. 123456789.dkr.ecr.eu-west-2.amazonaws.com/sudoku-image-recognition:latest)."
  type        = string
  default     = ""
}

variable "git_branch" {
  description = "The actual git branch name that Amplify should check out. For rc-* workspaces this may differ from the workspace name due to the 32-char workspace limit."
  type        = string
  default     = ""
}

variable "exclude_amplify_domain" {
  description = "Set to true to skip provisioning the production domain association"
  type        = bool
  default     = false
}

variable "exclude_amplify_beta_domain" {
  description = "Set to true to skip provisioning the beta domain association"
  type        = bool
  default     = false
}