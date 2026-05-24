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

  # Deployment package already uploaded to S3
  create_package = false
  s3_existing_package = {
    bucket = local.lambda_zip_bucket_id
    key    = aws_s3_object.lambda_zip.key
  }

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
    CORS_ALLOWED_ORIGINS = "https://${aws_amplify_branch.main.branch_name}.${aws_amplify_app.sudoku.default_domain},http://localhost:5173"
    DYNAMODB_TABLE_NAME  = aws_dynamodb_table.sudoku_games.name
    PLAYERS_TABLE_NAME   = aws_dynamodb_table.sudoku_players.name
    COGNITO_ISSUER_URL   = "https://cognito-idp.eu-west-2.amazonaws.com/${local.cognito_user_pool_id}"
    COGNITO_CLIENT_ID    = local.cognito_web_client_id
  }

  # For the default workspace the Lambda has been invoked before and auto-created
  # its log group; tell the module to adopt it. For RC environments the Lambda is
  # brand-new so there is no existing log group — let the module create it.
  use_existing_cloudwatch_log_group = local.is_default

  # checkov:skip=CKV_AWS_116: Synchronous HTTP API invocation — DLQ only applies to async Lambda invocations
  # checkov:skip=CKV_AWS_117: No VPC required — adding one would incur NAT Gateway cost (~$32/month) with no security benefit for this public API
  # checkov:skip=CKV_AWS_272: Single-developer project — AWS Signer code-signing setup not warranted
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
