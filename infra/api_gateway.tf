# ── CloudWatch log group (standalone to preserve existing resource name) ──────
# Kept outside the module so Terraform does not rename/recreate the log group.
resource "aws_cloudwatch_log_group" "api_gateway" {
  name              = "/aws/apigateway/sudoku${local.suffix}"
  retention_in_days = local.is_default ? 7 : 3

  # checkov:skip=CKV_AWS_338: 7-day retention is intentional to minimise log storage cost for a personal project
  # checkov:skip=CKV_AWS_158: KMS CMK encryption costs ~$1/month with no meaningful benefit over AWS-managed encryption here
}

# ── API Gateway (module-managed) ──────────────────────────────────────────────
# CORS origins are set as a baseline here; the deploy workflow tightens them to
# the exact Amplify URL post-apply. The module (v5) does not expose a lifecycle
# ignore_changes block, so the post-deploy script must re-tighten CORS each run.
module "api_gateway" {
  source  = "terraform-aws-modules/apigateway-v2/aws"
  version = "~> 6.1"

  name          = "sudoku${local.suffix}"
  protocol_type = "HTTP"

  # No custom domain on the API Gateway — custom domains are handled by Amplify
  create_domain_name    = false
  create_domain_records = false
  create_certificate    = false

  cors_configuration = {
    allow_methods = ["GET", "POST", "PATCH", "OPTIONS"]
    allow_origins = ["http://localhost:5173"]
    allow_headers = ["Content-Type", "Authorization"]
    max_age       = 300
  }

  # Stage
  create_stage = true
  stage_name   = "$default"
  deploy_stage = true

  stage_default_route_settings = {
    throttling_burst_limit = var.api_gateway_throttle_burst_limit
    throttling_rate_limit  = var.api_gateway_throttle_rate_limit
  }

  stage_access_log_settings = {
    create_log_group = false
    destination_arn  = aws_cloudwatch_log_group.api_gateway.arn
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

  # JWT Authorizer
  authorizers = {
    cognito_jwt = {
      authorizer_type  = "JWT"
      identity_sources = ["$request.header.Authorization"]
      name             = "cognito-jwt${local.suffix}"
      jwt_configuration = {
        issuer   = "https://cognito-idp.eu-west-2.amazonaws.com/${local.cognito_user_pool_id}"
        audience = [local.cognito_web_client_id, local.cognito_smoke_client_id]
      }
    }
  }

  # Routes — integrations are embedded per-route in this module version.
  # $default is public; all /api/v1/* game and player routes require JWT.
  # checkov:skip=CKV_AWS_309: $default catches only public routes (/puzzles/*, /health) — game routes use explicit JWT-protected routes below
  routes = {
    "$default" = {
      integration = {
        uri                    = aws_lambda_alias.live.invoke_arn
        payload_format_version = "1.0"
      }
    }

    "POST /api/v1/games" = {
      authorization_type = "JWT"
      authorizer_key     = "cognito_jwt"
      integration = {
        uri                    = aws_lambda_alias.live.invoke_arn
        payload_format_version = "1.0"
      }
    }

    "POST /api/v1/games/from-image" = {
      authorization_type = "JWT"
      authorizer_key     = "cognito_jwt"
      integration = {
        uri                    = aws_lambda_alias.live.invoke_arn
        payload_format_version = "1.0"
      }
    }

    "GET /api/v1/games/{gameId}" = {
      authorization_type = "JWT"
      authorizer_key     = "cognito_jwt"
      integration = {
        uri                    = aws_lambda_alias.live.invoke_arn
        payload_format_version = "1.0"
      }
    }

    "PATCH /api/v1/games/{gameId}" = {
      authorization_type = "JWT"
      authorizer_key     = "cognito_jwt"
      integration = {
        uri                    = aws_lambda_alias.live.invoke_arn
        payload_format_version = "1.0"
      }
    }

    "GET /api/v1/games/current" = {
      authorization_type = "JWT"
      authorizer_key     = "cognito_jwt"
      integration = {
        uri                    = aws_lambda_alias.live.invoke_arn
        payload_format_version = "1.0"
      }
    }

    "GET /api/v1/players/me" = {
      authorization_type = "JWT"
      authorizer_key     = "cognito_jwt"
      integration = {
        uri                    = aws_lambda_alias.live.invoke_arn
        payload_format_version = "1.0"
      }
    }

    "POST /api/v1/puzzles/import" = {
      authorization_type = "JWT"
      authorizer_key     = "cognito_jwt"
      integration = {
        uri                    = module.image_recognition_lambda.lambda_function_invoke_arn
        payload_format_version = "2.0"
      }
    }

    "GET /api/v1/puzzles/import/warmup" = {
      integration = {
        uri                    = module.image_recognition_lambda.lambda_function_invoke_arn
        payload_format_version = "2.0"
      }
    }
  }
}
