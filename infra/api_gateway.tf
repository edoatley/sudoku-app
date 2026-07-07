locals {
  _lambda_integration = {
    uri                    = aws_lambda_alias.live.invoke_arn
    payload_format_version = "1.0"
  }

  base_routes = {
    "$default" = {
      integration = local._lambda_integration
    }

    "POST /api/v1/games" = {
      authorization_type = "JWT"
      authorizer_key     = "cognito_jwt"
      integration        = local._lambda_integration
    }

    "POST /api/v1/games/from-image" = {
      authorization_type = "JWT"
      authorizer_key     = "cognito_jwt"
      integration        = local._lambda_integration
    }

    "GET /api/v1/games/{gameId}" = {
      authorization_type = "JWT"
      authorizer_key     = "cognito_jwt"
      integration        = local._lambda_integration
    }

    "PATCH /api/v1/games/{gameId}" = {
      authorization_type = "JWT"
      authorizer_key     = "cognito_jwt"
      integration        = local._lambda_integration
    }

    "GET /api/v1/games/current" = {
      authorization_type = "JWT"
      authorizer_key     = "cognito_jwt"
      integration        = local._lambda_integration
    }

    "GET /api/v1/players/me" = {
      authorization_type = "JWT"
      authorizer_key     = "cognito_jwt"
      integration        = local._lambda_integration
    }

    "POST /api/v1/ai/image-to-puzzle" = {
      authorization_type = "JWT"
      authorizer_key     = "cognito_jwt"
      integration = {
        uri                    = module.image_recognition_lambda.lambda_function_invoke_arn
        payload_format_version = "2.0"
      }
    }

    "GET /api/v1/ai/image-to-puzzle/warmup" = {
      integration = {
        uri                    = module.image_recognition_lambda.lambda_function_invoke_arn
        payload_format_version = "2.0"
      }
    }
  }

  ai_coach_routes = {
    # Tighter global cap on the AI coach route — Bedrock calls are expensive compared to game ops.
    # This is a route-level global ceiling (not per-user); per-user rate limiting is enforced in Lambda.
    "POST /api/v1/ai/coach" = {
      authorization_type     = "JWT"
      authorizer_key         = "cognito_jwt"
      integration            = local._lambda_integration
      throttling_burst_limit = 10
      throttling_rate_limit  = 5
    }
  }
}

# ── CloudWatch log group (standalone to preserve existing resource name) ──────
# Kept outside the module so Terraform does not rename/recreate the log group.
resource "aws_cloudwatch_log_group" "api_gateway" {
  name              = "/aws/apigateway/sudoku${local.suffix}"
  retention_in_days = local.is_default ? 7 : 3

  # checkov:skip=CKV_AWS_338: 7-day retention is intentional to minimise log storage cost for a personal project
  # checkov:skip=CKV_AWS_158: KMS CMK encryption costs ~$1/month with no meaningful benefit over AWS-managed encryption here
}

# ── API Gateway (module-managed) ──────────────────────────────────────────────
# CORS origins are set to the static custom domain so that terraform apply always
# sets them correctly — no post-deploy CORS tightening step needed.
# The raw *.amplifyapp.com URL is intentionally excluded: referencing aws_amplify_app
# or aws_amplify_branch here would create a cycle (Amplify depends on api_endpoint).
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
    allow_origins = local.is_default ? [
      "https://sudoku.edoatley.co.uk",
      "http://localhost:5173",
      ] : [
      "https://sudoku-beta.edoatley.co.uk",
      "http://localhost:5173",
    ]
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

  # Routes — $default is public; game/player/scan routes require JWT.
  # ai_coach_routes are merged in when local.ai_coach_enabled is true (rc-* workspaces).
  # Image recognition scan routes live in base_routes — always enabled on all workspaces.
  # checkov:skip=CKV_AWS_309: $default catches only public routes (/puzzles/*, /health) — game and ai routes use explicit JWT-protected routes
  routes = {
    for k, v in merge(local.base_routes, local.ai_coach_routes) :
    k => v if !contains(keys(local.ai_coach_routes), k) || local.ai_coach_enabled
  }

  # checkov:skip=CKV_TF_1: Terraform Registry modules are version-pinned (~>6.1); commit-hash pinning requires forking off the registry
}
