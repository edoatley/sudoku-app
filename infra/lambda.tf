resource "aws_s3_bucket" "lambda_zip" {
  bucket = "sudoku-lambda-zip-${local.account_id}"

  # checkov:skip=CKV_AWS_18: Access logging not needed for a Lambda deployment artefact bucket
  # checkov:skip=CKV_AWS_21: Versioning intentionally disabled — lifecycle rule expires objects after 30 days
  # checkov:skip=CKV_AWS_144: Cross-region replication adds ongoing cost; unnecessary for a deployment artefact bucket
  # checkov:skip=CKV_AWS_145: KMS CMK encryption costs ~$1/month; SSE-S3 (default) is sufficient for deployment artefacts
  # checkov:skip=CKV2_AWS_62: Event notifications have no use case for a deployment artefact bucket
}

resource "aws_s3_bucket_public_access_block" "lambda_zip" {
  bucket = aws_s3_bucket.lambda_zip.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_lifecycle_configuration" "lambda_zip" {
  bucket = aws_s3_bucket.lambda_zip.id

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
  bucket = aws_s3_bucket.lambda_zip.id
  key    = "function.zip"
  source = var.lambda_zip_path
  etag   = filemd5(var.lambda_zip_path)
}

resource "aws_lambda_function" "sudoku" {
  function_name = "sudoku"
  role          = aws_iam_role.lambda_exec.arn

  s3_bucket        = aws_s3_bucket.lambda_zip.id
  s3_key           = aws_s3_object.lambda_zip.key
  source_code_hash = aws_s3_object.lambda_zip.etag

  runtime       = "java21"
  handler       = "io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest"
  architectures = ["x86_64"]
  memory_size   = 512
  timeout       = 8
  publish       = true

  snap_start {
    apply_on = "PublishedVersions"
  }

  tracing_config {
    mode = "PassThrough" # X-Ray traces only when upstream sends trace header — zero cost
  }

  reserved_concurrent_executions = 10

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
