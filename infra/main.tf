data "aws_caller_identity" "current" {}

locals {
  account_id = data.aws_caller_identity.current.account_id
  is_default = terraform.workspace == "default"
  is_rc      = startswith(terraform.workspace, "rc-")
  suffix     = local.is_default ? "" : "-${terraform.workspace}"

  lambda_zip_bucket_id = local.is_default ? aws_s3_bucket.lambda_zip[0].id : data.aws_s3_bucket.lambda_zip_shared[0].id

  # Cognito references — rc workspaces share a single pool; others own theirs
  cognito_user_pool_id        = local.is_rc ? data.aws_cognito_user_pools.rc_shared[0].ids[0] : aws_cognito_user_pool.main[0].id
  cognito_domain              = local.is_rc ? "sudoku-auth-rc" : "sudoku-auth${local.suffix}"
  cognito_web_client_id       = local.is_rc ? (length(data.aws_cognito_user_pool_client.rc_web) > 0 ? data.aws_cognito_user_pool_client.rc_web[0].id : var.rc_cognito_web_client_id) : aws_cognito_user_pool_client.web[0].id
  cognito_smoke_client_id     = local.is_rc ? (length(data.aws_cognito_user_pool_client.rc_smoke) > 0 ? data.aws_cognito_user_pool_client.rc_smoke[0].id : var.rc_cognito_smoke_client_id) : aws_cognito_user_pool_client.smoke_test[0].id
  cognito_smoke_client_secret = local.is_rc ? (length(data.aws_cognito_user_pool_client.rc_smoke) > 0 ? data.aws_cognito_user_pool_client.rc_smoke[0].client_secret : "") : aws_cognito_user_pool_client.smoke_test[0].client_secret
}
