resource "aws_cognito_user_pool" "main" {
  name = "sudoku${local.suffix}"

  # Social-only pool — block native username/password sign-up
  admin_create_user_config {
    allow_admin_create_user_only = true
  }

  auto_verified_attributes = ["email"]

  # email and name are mapped from the Google IdP token
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

  # Password policy is required by the resource even for social-only pools
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

resource "aws_cognito_user_pool_domain" "main" {
  domain       = "sudoku-auth${local.suffix}"
  user_pool_id = aws_cognito_user_pool.main.id
}

resource "aws_cognito_identity_provider" "google" {
  user_pool_id  = aws_cognito_user_pool.main.id
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

# ---------------------------------------------------------------------------
# Smoke-test user — admin-created, username/password auth only.
# Never surfaced in the UI; used exclusively by CI to obtain a real JWT.
# ---------------------------------------------------------------------------
resource "aws_cognito_user" "smoke_test" {
  user_pool_id = aws_cognito_user_pool.main.id
  username     = var.smoke_test_user_email

  attributes = {
    email          = var.smoke_test_user_email
    email_verified = "true"
    name           = "Smoke Test User"
  }

  # Set a permanent password so CI can authenticate without a challenge flow
  password             = var.smoke_test_user_password
  message_action       = "SUPPRESS" # Don't send a welcome email
  force_alias_creation = false
}

# Server-side app client used only by CI — has a client secret and enables
# USER_PASSWORD_AUTH so the smoke test can exchange credentials for tokens.
# The public web client remains social-only.
resource "aws_cognito_user_pool_client" "smoke_test" {
  name         = "sudoku-smoke-test${local.suffix}"
  user_pool_id = aws_cognito_user_pool.main.id

  generate_secret = true # Server-side client; secret kept in GitHub Actions secrets

  allowed_oauth_flows                  = []
  allowed_oauth_flows_user_pool_client = false
  allowed_oauth_scopes                 = []
  supported_identity_providers         = ["COGNITO"]

  # Allow direct username/password auth — needed for CI token acquisition
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

resource "aws_cognito_user_pool_client" "web" {
  name         = "sudoku-web${local.suffix}"
  user_pool_id = aws_cognito_user_pool.main.id

  generate_secret = false # Public SPA client — secret cannot be kept in browser JS

  allowed_oauth_flows                  = ["code"]
  allowed_oauth_flows_user_pool_client = true
  allowed_oauth_scopes                 = ["openid", "email", "profile"]

  # Only Google — explicitly excludes native username/password login
  supported_identity_providers = ["Google"]

  # No SRP or password flows — social-only
  explicit_auth_flows = ["ALLOW_REFRESH_TOKEN_AUTH"]

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

  # Callback URL circular dependency: Amplify URL is not known until after first apply.
  # Provisioned with a broad wildcard baseline; the deploy workflow tightens this to the
  # exact Amplify URL immediately after terraform apply (same pattern as CORS).
  callback_urls = ["https://*.amplifyapp.com/", "http://localhost:5173/"]
  logout_urls   = ["https://*.amplifyapp.com/", "http://localhost:5173/"]

  lifecycle {
    ignore_changes = [callback_urls, logout_urls]
  }

  depends_on = [aws_cognito_identity_provider.google]
}
