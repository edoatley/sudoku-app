resource "aws_amplify_app" "sudoku" {
  name         = "sudoku"
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
    VITE_API_URL  = aws_apigatewayv2_stage.default.invoke_url
    VITE_MOCK_API = "false"
  }

  auto_branch_creation_config {
    enable_auto_build = true
  }

  enable_auto_branch_creation = true
}

resource "aws_amplify_branch" "main" {
  app_id      = aws_amplify_app.sudoku.id
  branch_name = "main"
  stage       = "PRODUCTION"

  enable_auto_build = true
}
