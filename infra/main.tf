data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

locals {
  account_id = data.aws_caller_identity.current.account_id
  aws_region = data.aws_region.current.region
  is_default = terraform.workspace == "default"
  is_rc      = startswith(terraform.workspace, "rc-")
  suffix     = local.is_default ? "" : "-${terraform.workspace}"

  # Bedrock inference profiles used by the image recognition Lambda.
  # This list is the single source of truth: IAM permissions and the Lambda
  # BEDROCK_MODELS env var are both derived from it.
  bedrock_models = [
    "eu.anthropic.claude-haiku-4-5-20251001-v1:0",
  ]

  # Single source of truth for the Bedrock InvokeModel Resource list — shared by
  # SudokuCoachBedrockPolicy (iam.tf) and SudokuImageRecognitionBedrockPolicy
  # (image_recognition_lambda.tf) so a model change only needs editing here.
  bedrock_invoke_resources = concat(
    # Inference-profile ARNs — what the Lambda code actually calls
    [for model in local.bedrock_models : "arn:aws:bedrock:*:*:inference-profile/${model}"],
    # Foundation-model ARNs — required by Bedrock when the profile routes to a regional endpoint
    [for model in local.bedrock_models : "arn:aws:bedrock:*::foundation-model/${replace(model, "/^(eu|us|ap)\\./", "")}"]
  )

  lambda_zip_bucket_id = local.is_default ? aws_s3_bucket.lambda_zip[0].id : data.aws_s3_bucket.lambda_zip_shared[0].id

  # Feature flag: AI coach (Bedrock-powered chat). Disabled on the default/production
  # workspace until the feature has been sufficiently tested on rc-* branches.
  # To enable on production: change to `true` (or remove the condition).
  ai_coach_enabled = !local.is_default

  # Cognito references — rc workspaces share a single pool; others own theirs
  cognito_user_pool_id        = local.is_rc ? data.aws_cognito_user_pools.rc_shared[0].ids[0] : aws_cognito_user_pool.main[0].id
  cognito_domain              = local.is_rc ? "sudoku-auth-rc" : "sudoku-auth${local.suffix}"
  cognito_web_client_id       = local.is_rc ? (length(data.aws_cognito_user_pool_client.rc_web) > 0 ? data.aws_cognito_user_pool_client.rc_web[0].id : var.rc_cognito_web_client_id) : aws_cognito_user_pool_client.web[0].id
  cognito_smoke_client_id     = local.is_rc ? (length(data.aws_cognito_user_pool_client.rc_smoke) > 0 ? data.aws_cognito_user_pool_client.rc_smoke[0].id : var.rc_cognito_smoke_client_id) : aws_cognito_user_pool_client.smoke_test[0].id
  cognito_smoke_client_secret = local.is_rc ? (length(data.aws_cognito_user_pool_client.rc_smoke) > 0 ? data.aws_cognito_user_pool_client.rc_smoke[0].client_secret : "") : aws_cognito_user_pool_client.smoke_test[0].client_secret
}
