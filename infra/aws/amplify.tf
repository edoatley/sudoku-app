# @spec CP-INFRA-041, CP-INFRA-042
resource "aws_amplify_app" "sudoku" {
  name         = "sudoku${local.suffix}"
  repository   = "https://github.com/${var.github_owner}/${var.github_repo}"
  access_token = var.github_token

  build_spec = <<-EOT
    version: 1
    frontend:
      phases:
        preBuild:
          commands:
            - cd ui && npm ci
        build:
          commands:
            - npm run build
      artifacts:
        baseDirectory: ui/dist
        files:
          - '**/*'
      cache:
        paths:
          - ui/node_modules/**/*
  EOT

  environment_variables = {
    VITE_API_URL              = "${module.api_gateway.api_endpoint}/api/v1"
    VITE_MOCK_API             = "false"
    VITE_COGNITO_USER_POOL_ID = local.cognito_user_pool_id
    VITE_COGNITO_CLIENT_ID    = local.cognito_web_client_id
    VITE_COGNITO_DOMAIN       = "${local.cognito_domain}.auth.${local.aws_region}.amazoncognito.com"
    VITE_DEV_TOOLS            = local.is_default ? "false" : "true"
    VITE_AI_COACH             = local.ai_coach_enabled ? "true" : "false"
  }

  auto_branch_creation_config {
    enable_auto_build = false
  }

  # Off everywhere — RC apps (where branch churn actually happens) already had
  # it off; it was previously on for the production app only, which meant any
  # pushed git branch created an inert-but-noisy Amplify branch there.
  enable_auto_branch_creation = false
}

# @spec CP-INFRA-040
resource "aws_amplify_branch" "main" {
  app_id      = aws_amplify_app.sudoku.id
  branch_name = local.is_default ? "main" : (var.git_branch != "" ? var.git_branch : terraform.workspace)
  stage       = local.is_default ? "PRODUCTION" : "DEVELOPMENT"

  # Auto-build is disabled so that the CI workflow can trigger the build
  # *after* terraform apply has set the correct VITE_API_URL env var.
  # If auto-build were enabled, Amplify would start building on push before
  # Terraform runs, baking a stale (or missing) API URL into the JS bundle.
  # @spec CP-INFRA-040
  enable_auto_build = false
}
