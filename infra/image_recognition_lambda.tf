# ---------------------------------------------------------------------------
# Image Recognition Lambda — Bedrock-backed puzzle import
# ---------------------------------------------------------------------------
# Deployed as a container image (Python 3.12 + Pillow) because Pillow requires
# native shared libraries that cannot be packaged in a plain Lambda zip.
# ---------------------------------------------------------------------------

# ── ECR repository ────────────────────────────────────────────────────────────
# A single shared repository is used for all environments (main + RC branches).
# Branch-prefixed tags distinguish images: <branch>-<sha> and <branch>-latest.
# The repository is managed by scripts/bootstrap.sh, not Terraform, so it is
# never accidentally destroyed during terraform destroy of an RC workspace.
data "aws_ecr_repository" "image_recognition" {
  name = "sudoku-image-recognition"
}

# ── IAM role ──────────────────────────────────────────────────────────────────

resource "aws_iam_role" "image_recognition_lambda_exec" {
  name = "SudokuImageRecognitionExecRole${local.suffix}"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect    = "Allow"
        Principal = { Service = "lambda.amazonaws.com" }
        Action    = "sts:AssumeRole"
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "image_recognition_basic_execution" {
  role       = aws_iam_role.image_recognition_lambda_exec.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_policy" "image_recognition_bedrock" {
  name        = "SudokuImageRecognitionBedrockPolicy${local.suffix}"
  description = "Grants the image recognition Lambda permission to call Nova Pro and Nova Lite via Bedrock Converse"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = ["bedrock:InvokeModel"]
        Resource = [
          # Foundation-model ARNs (direct invocation — Mistral, Nemotron)
          "arn:aws:bedrock:*::foundation-model/amazon.nova-pro-v1:0",
          "arn:aws:bedrock:*::foundation-model/amazon.nova-lite-v1:0",
          "arn:aws:bedrock:*::foundation-model/mistral.magistral-small-2509",
          "arn:aws:bedrock:*::foundation-model/nvidia.nemotron-nano-12b-v2",
          "arn:aws:bedrock:*::foundation-model/anthropic.claude-haiku-4-5-20251001-v1:0",
          # Cross-region inference-profile ARNs (what the Lambda code actually calls for us.* and global.* model IDs)
          "arn:aws:bedrock:*:*:inference-profile/us.amazon.nova-pro-v1:0",
          "arn:aws:bedrock:*:*:inference-profile/us.amazon.nova-lite-v1:0",
          "arn:aws:bedrock:*:*:inference-profile/global.anthropic.claude-haiku-4-5-20251001-v1:0",
        ]
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "image_recognition_bedrock" {
  role       = aws_iam_role.image_recognition_lambda_exec.name
  policy_arn = aws_iam_policy.image_recognition_bedrock.arn
}

# ── Lambda function ───────────────────────────────────────────────────────────

resource "aws_lambda_function" "image_recognition" {
  function_name = "sudoku-image-recognition${local.suffix}"
  role          = aws_iam_role.image_recognition_lambda_exec.arn

  package_type = "Image"
  image_uri    = var.image_recognition_image_uri

  architectures = ["x86_64"]
  memory_size   = 512
  timeout       = 60 # Bedrock inference can take ~20 s; cold start on a container image adds ~20 s on top

  tracing_config {
    mode = "PassThrough"
  }

  # checkov:skip=CKV_AWS_116: Synchronous HTTP API invocation — DLQ only applies to async Lambda invocations
  # checkov:skip=CKV_AWS_117: No VPC required — adding one would incur NAT Gateway cost (~$32/month) with no security benefit
  # checkov:skip=CKV_AWS_272: Single-developer project — AWS Signer code-signing not applicable to container images
  # checkov:skip=CKV_AWS_50: X-Ray active tracing costs money; PassThrough is sufficient for a personal project
}

resource "aws_cloudwatch_log_group" "image_recognition_lambda" {
  name              = "/aws/lambda/${aws_lambda_function.image_recognition.function_name}"
  retention_in_days = local.is_default ? 7 : 3

  # checkov:skip=CKV_AWS_338: 7-day retention is intentional to minimise log storage cost
  # checkov:skip=CKV_AWS_158: KMS CMK encryption costs ~$1/month; AWS-managed encryption is sufficient
}

resource "aws_lambda_permission" "image_recognition_api_gateway" {
  statement_id  = "AllowAPIGatewayInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.image_recognition.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.sudoku.execution_arn}/*/*"
}

# ── API Gateway integration + route ──────────────────────────────────────────

resource "aws_apigatewayv2_integration" "image_recognition" {
  api_id                 = aws_apigatewayv2_api.sudoku.id
  integration_type       = "AWS_PROXY"
  integration_uri        = aws_lambda_function.image_recognition.invoke_arn
  payload_format_version = "2.0"
}

resource "aws_apigatewayv2_route" "post_puzzles_import" {
  api_id             = aws_apigatewayv2_api.sudoku.id
  route_key          = "POST /api/v1/puzzles/import"
  target             = "integrations/${aws_apigatewayv2_integration.image_recognition.id}"
  authorization_type = "JWT"
  authorizer_id      = aws_apigatewayv2_authorizer.cognito_jwt.id
}

resource "aws_apigatewayv2_route" "get_puzzles_import_warmup" {
  api_id    = aws_apigatewayv2_api.sudoku.id
  route_key = "GET /api/v1/puzzles/import/warmup"
  target    = "integrations/${aws_apigatewayv2_integration.image_recognition.id}"
}
