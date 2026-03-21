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
    VITE_API_URL  = "${trimsuffix(aws_apigatewayv2_stage.default.invoke_url, "/")}/api/v1"
    VITE_MOCK_API = "false"
  }

  auto_branch_creation_config {
    enable_auto_build = false
  }

  enable_auto_branch_creation = local.is_default
}

resource "aws_amplify_branch" "main" {
  app_id      = aws_amplify_app.sudoku.id
  branch_name = local.is_default ? "main" : terraform.workspace
  stage       = local.is_default ? "PRODUCTION" : "DEVELOPMENT"

  # Auto-build is disabled so that the CI workflow can trigger the build
  # *after* terraform apply has set the correct VITE_API_URL env var.
  # If auto-build were enabled, Amplify would start building on push before
  # Terraform runs, baking a stale (or missing) API URL into the JS bundle.
  enable_auto_build = false
}
