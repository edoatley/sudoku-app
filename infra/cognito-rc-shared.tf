# ---------------------------------------------------------------------------
# Shared rc-* Cognito user pool.
# Applied ONLY in the "rc-shared" Terraform workspace.
# All rc-* branch workspaces reference this pool via data sources in cognito.tf.
#
# One-time setup: run `terraform workspace select rc-shared && terraform apply`
# after provisioning the initial rc-* environment.
# ---------------------------------------------------------------------------

resource "aws_cognito_user_pool" "rc_shared" {
  count = terraform.workspace == "rc-shared" ? 1 : 0
  name  = "sudoku-rc"

  admin_create_user_config {
    allow_admin_create_user_only = true
  }

  auto_verified_attributes = ["email"]

  schema {
    name                = "email"
    attribute_data_type = "String"
    required            = true
    mutable             = true
    string_attribute_constraints {
      min_length = 0
      max_length = 2048
    }
  }

  schema {
    name                = "name"
    attribute_data_type = "String"
    required            = false
    mutable             = true
    string_attribute_constraints {
      min_length = 0
      max_length = 2048
    }
  }

  mfa_configuration = "OFF"

  password_policy {
    minimum_length                   = 8
    require_lowercase                = false
    require_numbers                  = false
    require_symbols                  = false
    require_uppercase                = false
    temporary_password_validity_days = 1
  }

  # checkov:skip=CKV_AWS_131: Advanced security mode costs ~$0.05/MAU; not justified for this personal project
  # checkov:skip=CKV2_AWS_131: Advanced security mode costs ~$0.05/MAU; not justified for this personal project
}

resource "aws_cognito_user_pool_domain" "rc_shared" {
  count        = terraform.workspace == "rc-shared" ? 1 : 0
  domain       = "sudoku-auth-rc"
  user_pool_id = aws_cognito_user_pool.rc_shared[0].id
}

resource "aws_cognito_identity_provider" "rc_shared_google" {
  count         = terraform.workspace == "rc-shared" ? 1 : 0
  user_pool_id  = aws_cognito_user_pool.rc_shared[0].id
  provider_name = "Google"
  provider_type = "Google"

  provider_details = {
    client_id                     = var.google_client_id
    client_secret                 = var.google_client_secret
    authorize_scopes              = "openid email profile"
    attributes_url                = "https://people.googleapis.com/v1/people/me?personFields="
    attributes_url_add_attributes = "true"
    authorize_url                 = "https://accounts.google.com/o/oauth2/v2/auth"
    oidc_issuer                   = "https://accounts.google.com"
    token_request_method          = "POST"
    token_url                     = "https://oauth2.googleapis.com/token"
  }

  attribute_mapping = {
    email    = "email"
    name     = "name"
    username = "sub"
  }
}

resource "aws_cognito_user_pool_client" "rc_shared_web" {
  count        = terraform.workspace == "rc-shared" ? 1 : 0
  name         = "sudoku-web-rc"
  user_pool_id = aws_cognito_user_pool.rc_shared[0].id

  generate_secret                      = false
  allowed_oauth_flows                  = ["code"]
  allowed_oauth_flows_user_pool_client = true
  allowed_oauth_scopes                 = ["openid", "email", "profile"]
  supported_identity_providers         = ["Google"]
  # USER_PASSWORD_AUTH enabled for smoke-test CI token acquisition.
  # The smoke test user is admin-created and never surfaced in the UI.
  explicit_auth_flows           = ["ALLOW_USER_PASSWORD_AUTH", "ALLOW_REFRESH_TOKEN_AUTH"]
  enable_token_revocation       = true
  prevent_user_existence_errors = "ENABLED"

  refresh_token_validity = 30
  access_token_validity  = 1
  id_token_validity      = 1

  token_validity_units {
    access_token  = "hours"
    id_token      = "hours"
    refresh_token = "days"
  }

  # Cognito does not support wildcard callback URLs — they are accepted by the API
  # but silently rejected during the OAuth flow (returns HTTP 400).
  # The deploy workflow adds each rc-* branch's exact Amplify URL after terraform apply.
  # ignore_changes prevents Terraform from reverting the accumulated URL list on each run.
  callback_urls = ["http://localhost:5173/"]
  logout_urls   = ["http://localhost:5173/"]

  lifecycle {
    ignore_changes = [callback_urls, logout_urls]
  }

  depends_on = [aws_cognito_identity_provider.rc_shared_google]
}

resource "aws_cognito_user_pool_client" "rc_shared_smoke" {
  count        = terraform.workspace == "rc-shared" ? 1 : 0
  name         = "sudoku-smoke-test-rc"
  user_pool_id = aws_cognito_user_pool.rc_shared[0].id

  generate_secret                      = true
  allowed_oauth_flows                  = []
  allowed_oauth_flows_user_pool_client = false
  allowed_oauth_scopes                 = []
  supported_identity_providers         = ["COGNITO"]

  explicit_auth_flows = [
    "ALLOW_USER_PASSWORD_AUTH",
    "ALLOW_REFRESH_TOKEN_AUTH",
  ]

  enable_token_revocation = true

  refresh_token_validity = 1
  access_token_validity  = 1
  id_token_validity      = 1

  token_validity_units {
    access_token  = "hours"
    id_token      = "hours"
    refresh_token = "days"
  }
}

resource "aws_cognito_user" "rc_shared_smoke_test" {
  count        = terraform.workspace == "rc-shared" ? 1 : 0
  user_pool_id = aws_cognito_user_pool.rc_shared[0].id
  username     = var.smoke_test_user_email

  attributes = {
    email          = var.smoke_test_user_email
    email_verified = "true"
    name           = "Smoke Test User"
  }

  password             = var.smoke_test_user_password
  message_action       = "SUPPRESS"
  force_alias_creation = false
}

resource "aws_cognito_user_group" "rc_admin" {
  count        = terraform.workspace == "rc-shared" ? 1 : 0
  name         = "administrators"
  user_pool_id = aws_cognito_user_pool.rc_shared[0].id
  description  = "Members may reach /admin/* endpoints"
}

output "rc_shared_cognito_user_pool_id" {
  description = "Shared rc-* Cognito User Pool ID (rc-shared workspace only)."
  value       = terraform.workspace == "rc-shared" ? aws_cognito_user_pool.rc_shared[0].id : null
}

output "rc_shared_cognito_web_client_id" {
  description = "Shared rc-* web app client ID (rc-shared workspace only)."
  value       = terraform.workspace == "rc-shared" ? aws_cognito_user_pool_client.rc_shared_web[0].id : null
}

output "rc_shared_cognito_smoke_client_id" {
  description = "Shared rc-* smoke-test client ID (rc-shared workspace only)."
  value       = terraform.workspace == "rc-shared" ? aws_cognito_user_pool_client.rc_shared_smoke[0].id : null
}
