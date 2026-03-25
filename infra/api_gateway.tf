resource "aws_apigatewayv2_api" "sudoku" {
  name          = "sudoku${local.suffix}"
  protocol_type = "HTTP"

  # Broad wildcard used as the Terraform-managed baseline to avoid a circular
  # dependency (Amplify needs the API Gateway invoke URL, so the exact Amplify
  # domain cannot be referenced here). The deploy workflow tightens this to the
  # exact Amplify URL immediately after `terraform apply` via a post-apply step.
  cors_configuration {
    allow_methods = ["GET", "POST", "PATCH", "OPTIONS"]
    allow_origins = [
      "http://localhost:5173"
    ]
    allow_headers = ["Content-Type", "Authorization"]
    max_age       = 300
  }

  lifecycle {
    # CORS origins are tightened post-apply by the deploy workflow.
    # Ignore here so Terraform does not revert the exact Amplify URL on the next run.
    ignore_changes = [cors_configuration]
  }
}

resource "aws_apigatewayv2_integration" "lambda" {
  api_id                 = aws_apigatewayv2_api.sudoku.id
  integration_type       = "AWS_PROXY"
  integration_uri        = aws_lambda_alias.live.arn
  payload_format_version = "1.0"
}

resource "aws_apigatewayv2_route" "default" {
  api_id    = aws_apigatewayv2_api.sudoku.id
  route_key = "$default"
  target    = "integrations/${aws_apigatewayv2_integration.lambda.id}"

  # checkov:skip=CKV_AWS_309: $default catches only public routes (/puzzles/*, /health) — game routes use explicit JWT-protected routes below
}

# ── JWT Authorizer ────────────────────────────────────────────────────────────

resource "aws_apigatewayv2_authorizer" "cognito_jwt" {
  api_id           = aws_apigatewayv2_api.sudoku.id
  authorizer_type  = "JWT"
  identity_sources = ["$request.header.Authorization"]
  name             = "cognito-jwt${local.suffix}"

  jwt_configuration {
    issuer   = "https://cognito-idp.eu-west-2.amazonaws.com/${local.cognito_user_pool_id}"
    audience = [local.cognito_web_client_id, local.cognito_smoke_client_id]
  }
}

# ── Protected routes (JWT required) ──────────────────────────────────────────
# HTTP API v2 uses most-specific-match routing, so these take priority over $default.

resource "aws_apigatewayv2_route" "post_games" {
  api_id             = aws_apigatewayv2_api.sudoku.id
  route_key          = "POST /api/v1/games"
  target             = "integrations/${aws_apigatewayv2_integration.lambda.id}"
  authorization_type = "JWT"
  authorizer_id      = aws_apigatewayv2_authorizer.cognito_jwt.id
}

resource "aws_apigatewayv2_route" "post_games_from_image" {
  api_id             = aws_apigatewayv2_api.sudoku.id
  route_key          = "POST /api/v1/games/from-image"
  target             = "integrations/${aws_apigatewayv2_integration.lambda.id}"
  authorization_type = "JWT"
  authorizer_id      = aws_apigatewayv2_authorizer.cognito_jwt.id
}

resource "aws_apigatewayv2_route" "get_game" {
  api_id             = aws_apigatewayv2_api.sudoku.id
  route_key          = "GET /api/v1/games/{gameId}"
  target             = "integrations/${aws_apigatewayv2_integration.lambda.id}"
  authorization_type = "JWT"
  authorizer_id      = aws_apigatewayv2_authorizer.cognito_jwt.id
}

resource "aws_apigatewayv2_route" "patch_game" {
  api_id             = aws_apigatewayv2_api.sudoku.id
  route_key          = "PATCH /api/v1/games/{gameId}"
  target             = "integrations/${aws_apigatewayv2_integration.lambda.id}"
  authorization_type = "JWT"
  authorizer_id      = aws_apigatewayv2_authorizer.cognito_jwt.id
}

resource "aws_apigatewayv2_route" "get_player_me" {
  api_id             = aws_apigatewayv2_api.sudoku.id
  route_key          = "GET /api/v1/players/me"
  target             = "integrations/${aws_apigatewayv2_integration.lambda.id}"
  authorization_type = "JWT"
  authorizer_id      = aws_apigatewayv2_authorizer.cognito_jwt.id
}

resource "aws_apigatewayv2_stage" "default" {
  api_id      = aws_apigatewayv2_api.sudoku.id
  name        = "$default"
  auto_deploy = true

  default_route_settings {
    throttling_burst_limit = var.api_gateway_throttle_burst_limit
    throttling_rate_limit  = var.api_gateway_throttle_rate_limit
  }

  access_log_settings {
    destination_arn = aws_cloudwatch_log_group.api_gateway.arn
    format = jsonencode({
      requestId      = "$context.requestId"
      ip             = "$context.identity.sourceIp"
      requestTime    = "$context.requestTime"
      httpMethod     = "$context.httpMethod"
      routeKey       = "$context.routeKey"
      status         = "$context.status"
      responseLength = "$context.responseLength"
    })
  }
}

resource "aws_cloudwatch_log_group" "api_gateway" {
  name              = "/aws/apigateway/sudoku${local.suffix}"
  retention_in_days = local.is_default ? 7 : 3

  # checkov:skip=CKV_AWS_338: 7-day retention is intentional to minimise log storage cost for a personal project
  # checkov:skip=CKV_AWS_158: KMS CMK encryption costs ~$1/month with no meaningful benefit over AWS-managed encryption here
}
