variable "github_token" {
  description = "GitHub classic OAuth token (repo scope) for Amplify repository connection. In CI: AMPLIFY_GITHUB_TOKEN secret. Locally: scripts/.env.local."
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

variable "backend_image_uri" {
  description = "ECR image URI for the backend Lambda (e.g. 123456789.dkr.ecr.eu-west-2.amazonaws.com/sudoku-backend:latest)."
  type        = string
  default     = ""

  validation {
    condition     = can(regex("^[0-9]+\\.dkr\\.ecr\\.[a-z0-9-]+\\.amazonaws\\.com/.+:.+$", var.backend_image_uri))
    error_message = "backend_image_uri must be a full ECR image URI (e.g. <account>.dkr.ecr.<region>.amazonaws.com/sudoku-backend:<tag>). Build and push one first (see the backend CI build job or scripts/infra/aws/bootstrap.sh), or pass -var \"backend_image_uri=...\" explicitly."
  }
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
  description = "Google OAuth 2.0 Client ID for Cognito social login. Source: Google Cloud Console → APIs & Services → Credentials. In CI: GOOGLE_CLIENT_ID secret. Locally: scripts/.env.local."
  type        = string
  sensitive   = true
}

variable "google_client_secret" {
  description = "Google OAuth 2.0 Client Secret for Cognito social login. Source: Google Cloud Console → APIs & Services → Credentials. In CI: GOOGLE_CLIENT_SECRET secret. Locally: scripts/.env.local."
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

  validation {
    condition     = can(regex("^[0-9]+\\.dkr\\.ecr\\.[a-z0-9-]+\\.amazonaws\\.com/.+:.+$", var.image_recognition_image_uri))
    error_message = "image_recognition_image_uri must be a full ECR image URI (e.g. <account>.dkr.ecr.<region>.amazonaws.com/sudoku-image-recognition:<tag>). Build and push one first (see the image-recognition CI build job or scripts/bootstrap.sh), or pass -var \"image_recognition_image_uri=...\" explicitly."
  }
}

variable "git_branch" {
  description = "The actual git branch name that Amplify should check out. For rc-* workspaces this may differ from the workspace name due to the 32-char workspace limit."
  type        = string
  default     = ""
}

variable "budget_alert_email" {
  description = "Email address for AWS Budgets and Cost Anomaly Detection alerts. Leave empty to skip creating budget resources."
  type        = string
  default     = ""
}

variable "bedrock_monthly_budget_usd" {
  description = "Monthly Bedrock spend cap in USD. Alerts at 80% actual and 100% forecasted; a budget action auto-attaches a Bedrock deny policy to the Lambda role at 100% actual. Only active when budget_alert_email is set."
  type        = string
  default     = "25"
}
