resource "aws_s3_bucket" "lambda_zip" {
  count  = local.is_default ? 1 : 0
  bucket = "sudoku-lambda-zip-${local.account_id}"

  # checkov:skip=CKV_AWS_18: Access logging not needed for a Lambda deployment artefact bucket
  # checkov:skip=CKV_AWS_21: Versioning intentionally disabled — lifecycle rule expires objects after 30 days
  # checkov:skip=CKV_AWS_144: Cross-region replication adds ongoing cost; unnecessary for a deployment artefact bucket
  # checkov:skip=CKV_AWS_145: KMS CMK encryption costs ~$1/month; SSE-S3 (default) is sufficient for deployment artefacts
  # checkov:skip=CKV2_AWS_62: Event notifications have no use case for a deployment artefact bucket
}

data "aws_s3_bucket" "lambda_zip_shared" {
  count  = local.is_default ? 0 : 1
  bucket = "sudoku-lambda-zip-${local.account_id}"
}

resource "aws_s3_bucket_public_access_block" "lambda_zip" {
  count  = local.is_default ? 1 : 0
  bucket = aws_s3_bucket.lambda_zip[0].id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_lifecycle_configuration" "lambda_zip" {
  count  = local.is_default ? 1 : 0
  bucket = aws_s3_bucket.lambda_zip[0].id

  rule {
    id     = "expire-old-zips"
    status = "Enabled"

    filter {}

    expiration {
      days = 30
    }

    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }
}

# Not consumed by module.lambda below — it deploys from local_existing_package
# (the local jar), not this object. Uploaded purely so deploy-local.sh can
# download it as a fallback when no local jar is present. etag forces
# re-upload detection but plays no role in Lambda versioning.
resource "aws_s3_object" "lambda_zip" {
  bucket = local.lambda_zip_bucket_id
  key    = "${terraform.workspace}/function.zip"
  source = var.lambda_zip_path
  etag   = filemd5(var.lambda_zip_path)
}

module "lambda" {
  source  = "terraform-aws-modules/lambda/aws"
  version = "~> 8.8"

  function_name = "sudoku${local.suffix}"
  description   = "Sudoku game API (Quarkus/Java)"

  # Use existing IAM role — avoids a destroy+create cycle for a role rename
  create_role = false
  lambda_role = aws_iam_role.lambda_exec.arn

  # CI downloads the built JAR to backend/target/function.zip before running Terraform.
  # local_existing_package causes the module to compute source_code_hash = filebase64sha256(path),
  # so Terraform detects code changes and publishes a new Lambda version on every JAR change.
  create_package         = false
  local_existing_package = var.lambda_zip_path

  runtime       = "java25"
  handler       = "io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest"
  architectures = ["x86_64"]
  memory_size   = 512
  timeout       = 8
  publish       = true

  snap_start   = true
  tracing_mode = "PassThrough"

  # reserved_concurrent_executions not set — account limit is 10 total, reserving any would
  # exhaust the 10-unit unreserved minimum AWS requires, causing a deployment error.

  environment_variables = {
    CORS_ALLOWED_ORIGINS = (
      local.is_default ? "https://sudoku.edoatley.co.uk,http://localhost:5173" :
      local.is_rc ? "https://sudoku-beta.edoatley.co.uk,http://localhost:5173" :
      "http://localhost:5173"
    )
    DYNAMODB_TABLE_NAME         = aws_dynamodb_table.sudoku_games.name
    PLAYERS_TABLE_NAME          = aws_dynamodb_table.sudoku_players.name
    COACH_RATE_LIMIT_TABLE_NAME = aws_dynamodb_table.sudoku_coach_rate_limits.name
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
