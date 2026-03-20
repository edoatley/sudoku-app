data "aws_caller_identity" "current" {}

locals {
  account_id = data.aws_caller_identity.current.account_id
  is_default = terraform.workspace == "default"
  suffix     = local.is_default ? "" : "-${terraform.workspace}"

  lambda_zip_bucket_id = local.is_default ? aws_s3_bucket.lambda_zip[0].id : data.aws_s3_bucket.lambda_zip_shared[0].id
}
