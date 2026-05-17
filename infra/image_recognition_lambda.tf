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
  description = "Grants the image recognition Lambda permission to invoke Bedrock inference profiles listed in local.bedrock_models"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = ["bedrock:InvokeModel"]
        Resource = concat(
          # Inference-profile ARNs — what the Lambda code actually calls
          [for model in local.bedrock_models : "arn:aws:bedrock:*:*:inference-profile/${model}"],
          # Foundation-model ARNs — required by Bedrock when the profile routes to a regional endpoint
          [for model in local.bedrock_models : "arn:aws:bedrock:*::foundation-model/${replace(model, "/^(eu|us|ap)\\./", "")}"]
        )
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "image_recognition_bedrock" {
  role       = aws_iam_role.image_recognition_lambda_exec.name
  policy_arn = aws_iam_policy.image_recognition_bedrock.arn
}

# ── Lambda function ───────────────────────────────────────────────────────────

module "image_recognition_lambda" {
  source  = "terraform-aws-modules/lambda/aws"
  version = "~> 7.0"

  function_name = "sudoku-image-recognition${local.suffix}"
  description   = "Sudoku image recognition (Bedrock-backed, container)"

  # Use existing IAM role — avoids a destroy+create cycle for a role rename
  create_role = false
  lambda_role = aws_iam_role.image_recognition_lambda_exec.arn

  package_type   = "Image"
  image_uri      = var.image_recognition_image_uri
  create_package = false

  architectures = ["x86_64"]
  memory_size   = 512
  timeout       = 60 # Bedrock inference can take ~20 s; cold start on a container image adds ~20 s on top
  tracing_mode  = "PassThrough"

  environment_variables = {
    AWS_REGION_NAME = "eu-west-2"
    BEDROCK_MODELS  = join(",", local.bedrock_models)
  }

  use_existing_cloudwatch_log_group = false
  cloudwatch_logs_retention_in_days = local.is_default ? 7 : 3

  # checkov:skip=CKV_AWS_116: Synchronous HTTP API invocation — DLQ only applies to async Lambda invocations
  # checkov:skip=CKV_AWS_117: No VPC required — adding one would incur NAT Gateway cost (~$32/month) with no security benefit
  # checkov:skip=CKV_AWS_272: Single-developer project — AWS Signer code-signing not applicable to container images
  # checkov:skip=CKV_AWS_50: X-Ray active tracing costs money; PassThrough is sufficient for a personal project
}

# Permission kept standalone — the module's allowed_triggers targets the published
# version qualifier, but the image recognition Lambda is unversioned/unaliased and
# this bespoke resource matches the original behaviour exactly.
resource "aws_lambda_permission" "image_recognition_api_gateway" {
  statement_id  = "AllowAPIGatewayInvoke"
  action        = "lambda:InvokeFunction"
  function_name = module.image_recognition_lambda.lambda_function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${module.api_gateway.api_execution_arn}/*/*"
}

