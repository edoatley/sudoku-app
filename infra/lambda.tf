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

resource "aws_lambda_function" "sudoku" {
  function_name = "sudoku${local.suffix}"
  role          = aws_iam_role.lambda_exec.arn

  s3_bucket        = local.lambda_zip_bucket_id
  s3_key           = aws_s3_object.lambda_zip.key
  source_code_hash = filebase64sha256(var.lambda_zip_path)

  runtime       = "java21"
  handler       = "io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest"
  architectures = ["x86_64"]
  memory_size   = 512
  timeout       = 8
  publish       = true

  environment {
    variables = {
      CORS_ALLOWED_ORIGINS = "https://${aws_amplify_branch.main.branch_name}.${aws_amplify_app.sudoku.default_domain},http://localhost:5173"
      DYNAMODB_TABLE_NAME  = aws_dynamodb_table.sudoku_games.name
      PLAYERS_TABLE_NAME   = aws_dynamodb_table.sudoku_players.name
      COGNITO_ISSUER_URL   = "https://cognito-idp.eu-west-2.amazonaws.com/${aws_cognito_user_pool.main.id}"
      COGNITO_CLIENT_ID    = aws_cognito_user_pool_client.web.id
    }
  }

  snap_start {
    apply_on = "PublishedVersions"
  }

  tracing_config {
    mode = "PassThrough" # X-Ray traces only when upstream sends trace header — zero cost
  }

  # reserved_concurrent_executions not set — account limit is 10 total, reserving any would
  # exhaust the 10-unit unreserved minimum AWS requires, causing a deployment error.

  # checkov:skip=CKV_AWS_116: Synchronous HTTP API invocation — DLQ only applies to async Lambda invocations
  # checkov:skip=CKV_AWS_117: No VPC required — adding one would incur NAT Gateway cost (~$32/month) with no security benefit for this public API
  # checkov:skip=CKV_AWS_272: Single-developer project — AWS Signer code-signing setup not warranted
}

resource "aws_lambda_alias" "live" {
  name             = "live"
  function_name    = aws_lambda_function.sudoku.function_name
  function_version = aws_lambda_function.sudoku.version
}

resource "aws_lambda_permission" "api_gateway" {
  statement_id  = "AllowAPIGatewayInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.sudoku.function_name
  qualifier     = aws_lambda_alias.live.name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.sudoku.execution_arn}/*/*"
}
