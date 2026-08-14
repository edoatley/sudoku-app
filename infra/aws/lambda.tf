# ── ECR repository ────────────────────────────────────────────────────────────
# A single shared repository is used for all environments (main + RC branches).
# Branch-prefixed tags distinguish images: <branch>-<sha> and <branch>-latest.
# The repository is managed by scripts/infra/aws/bootstrap.sh, not Terraform, so it is
# never accidentally destroyed during terraform destroy of an RC workspace.
data "aws_ecr_repository" "sudoku_backend" {
  name = "sudoku-backend"
}

module "lambda" {
  source  = "terraform-aws-modules/lambda/aws"
  version = "~> 8.8"

  function_name = "sudoku${local.suffix}"
  description   = "Sudoku game API (Quarkus/Java, container)"

  # Use existing IAM role — avoids a destroy+create cycle for a role rename
  create_role = false
  lambda_role = aws_iam_role.lambda_exec.arn

  package_type   = "Image"
  image_uri      = var.backend_image_uri
  create_package = false

  architectures = ["x86_64"]
  memory_size   = 512
  timeout       = 8
  publish       = true

  # SnapStart is not supported for container-image Lambda functions.
  tracing_mode = "PassThrough"

  # reserved_concurrent_executions not set — account limit is 10 total, reserving any would
  # exhaust the 10-unit unreserved minimum AWS requires, causing a deployment error.

  environment_variables = {
    # Third branch (neither default nor rc-*) is unreachable today — workspaces
    # are always one or the other — but falls back to localhost-only rather
    # than a custom domain that wouldn't exist for such a workspace.
    CORS_ALLOWED_ORIGINS = (
      local.is_default ? "https://sudoku.edoatley.co.uk,http://localhost:5173" :
      local.is_rc ? "https://sudoku-beta.edoatley.co.uk,http://localhost:5173" :
      "http://localhost:5173"
    )
    DYNAMODB_TABLE_NAME         = aws_dynamodb_table.sudoku_games.name
    PLAYERS_TABLE_NAME          = aws_dynamodb_table.sudoku_players.name
    COACH_RATE_LIMIT_TABLE_NAME = aws_dynamodb_table.sudoku_coach_rate_limits.name
    COACH_BEDROCK_API_MODE      = local.coach_bedrock_api_mode
    COGNITO_ISSUER_URL          = "https://cognito-idp.${local.aws_region}.amazonaws.com/${local.cognito_user_pool_id}"
    COGNITO_CLIENT_ID           = local.cognito_web_client_id
  }

  # Log group is managed as a standalone resource below (so retention can be set
  # uniformly, including on production's pre-existing group) — the module only
  # ever adopts it.
  use_existing_cloudwatch_log_group = true

  # checkov:skip=CKV_AWS_116: Synchronous HTTP API invocation — DLQ only applies to async Lambda invocations
  # checkov:skip=CKV_AWS_117: No VPC required — adding one would incur NAT Gateway cost (~$32/month) with no security benefit for this public API
  # checkov:skip=CKV_AWS_272: Single-developer project — AWS Signer code-signing setup not warranted
  # checkov:skip=CKV_TF_1: Terraform Registry modules are version-pinned (~>8.8); commit-hash pinning requires forking off the registry
}

# ── CloudWatch log group (standalone to control retention uniformly) ──────────
# Kept outside the module (same pattern as aws_cloudwatch_log_group.api_gateway)
# so a single resource sets retention for both freshly-created RC groups and
# production's group, which Lambda auto-created before Terraform managed it —
# see the moved{}/import{} blocks in migrations.tf.
resource "aws_cloudwatch_log_group" "lambda" {
  name              = "/aws/lambda/sudoku${local.suffix}"
  retention_in_days = local.is_default ? 7 : 3

  # checkov:skip=CKV_AWS_338: short retention is intentional to minimise log storage cost for a personal project
  # checkov:skip=CKV_AWS_158: KMS CMK encryption costs ~$1/month with no meaningful benefit over AWS-managed encryption here
}

# Alias and permission are kept standalone because the module (v7) does not
# manage aliases, and the permission must target the alias qualifier, not the
# published version that the module's allowed_triggers would target.
resource "aws_lambda_alias" "live" {
  name             = "live"
  function_name    = module.lambda.lambda_function_name
  function_version = module.lambda.lambda_function_version
}

resource "aws_lambda_permission" "api_gateway" {
  statement_id  = "AllowAPIGatewayInvoke"
  action        = "lambda:InvokeFunction"
  function_name = module.lambda.lambda_function_name
  qualifier     = aws_lambda_alias.live.name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${module.api_gateway.api_execution_arn}/*/*"
}
