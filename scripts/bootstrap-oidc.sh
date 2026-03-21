#!/usr/bin/env bash
# Bootstrap script — run once with valid AWS credentials before first Terraform apply.
# Creates: S3 state bucket, GitHub OIDC provider, GitHub Actions deploy role.
# Safe to re-run (idempotent).

set -euo pipefail

REGION="eu-west-2"
STATE_BUCKET="sudoku-tf-state"
OIDC_URL="https://token.actions.githubusercontent.com"
OIDC_AUDIENCE="sts.amazonaws.com"
DEPLOY_ROLE_NAME="sudoku-github-actions-deploy"
GITHUB_ORG="${GITHUB_ORG:-edoatley}"
GITHUB_REPO="${GITHUB_REPO:-sudoku-app}"

echo "==> Bootstrapping Sudoku infrastructure prerequisites"
echo "    Region:       ${REGION}"
echo "    State bucket: ${STATE_BUCKET}"
echo "    GitHub:       ${GITHUB_ORG}/${GITHUB_REPO}"
echo ""

ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
echo "    AWS account:  ${ACCOUNT_ID}"
echo ""

# ── 1. S3 state bucket ─────────────────────────────────────────────────────────
echo "==> [1/3] S3 state bucket: ${STATE_BUCKET}"

if aws s3api head-bucket --bucket "${STATE_BUCKET}" 2>/dev/null; then
  echo "    Already exists — skipping creation."
else
  aws s3api create-bucket \
    --bucket "${STATE_BUCKET}" \
    --region "${REGION}" \
    --create-bucket-configuration LocationConstraint="${REGION}"
  echo "    Created."
fi

aws s3api put-bucket-versioning \
  --bucket "${STATE_BUCKET}" \
  --versioning-configuration Status=Enabled

aws s3api put-bucket-encryption \
  --bucket "${STATE_BUCKET}" \
  --server-side-encryption-configuration '{
    "Rules": [{
      "ApplyServerSideEncryptionByDefault": {"SSEAlgorithm": "AES256"},
      "BucketKeyEnabled": true
    }]
  }'

aws s3api put-public-access-block \
  --bucket "${STATE_BUCKET}" \
  --public-access-block-configuration \
    BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true

aws s3api put-bucket-ownership-controls \
  --bucket "${STATE_BUCKET}" \
  --ownership-controls '{"Rules":[{"ObjectOwnership":"BucketOwnerEnforced"}]}'

echo "    Configured: versioning, SSE-S3, public access blocked, ACLs disabled."

# ── 2. GitHub Actions OIDC provider ────────────────────────────────────────────
echo "==> [2/3] GitHub Actions OIDC provider"

OIDC_ARN="arn:aws:iam::${ACCOUNT_ID}:oidc-provider/token.actions.githubusercontent.com"

if aws iam get-open-id-connect-provider --open-id-connect-provider-arn "${OIDC_ARN}" 2>/dev/null; then
  echo "    Already exists — skipping creation."
else
  # Fetch the thumbprint for token.actions.githubusercontent.com
  THUMBPRINT=$(openssl s_client -servername token.actions.githubusercontent.com \
    -showcerts -connect token.actions.githubusercontent.com:443 </dev/null 2>/dev/null \
    | openssl x509 -fingerprint -noout -sha1 \
    | sed 's/.*=//;s/://g' \
    | tr '[:upper:]' '[:lower:]')

  aws iam create-open-id-connect-provider \
    --url "${OIDC_URL}" \
    --client-id-list "${OIDC_AUDIENCE}" \
    --thumbprint-list "${THUMBPRINT}"
  echo "    Created (thumbprint: ${THUMBPRINT})."
fi

# ── 3. GitHub Actions deploy IAM role ──────────────────────────────────────────
echo "==> [3/3] IAM role: ${DEPLOY_ROLE_NAME}"

TRUST_POLICY=$(cat <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "${OIDC_ARN}"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "token.actions.githubusercontent.com:aud": "${OIDC_AUDIENCE}"
        },
        "StringLike": {
          "token.actions.githubusercontent.com:sub": "repo:${GITHUB_ORG}/${GITHUB_REPO}:*"
        }
      }
    }
  ]
}
EOF
)

DEPLOY_POLICY=$(cat <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "TerraformState",
      "Effect": "Allow",
      "Action": ["s3:GetObject", "s3:PutObject", "s3:DeleteObject", "s3:ListBucket"],
      "Resource": [
        "arn:aws:s3:::${STATE_BUCKET}",
        "arn:aws:s3:::${STATE_BUCKET}/*"
      ]
    },
    {
      "Sid": "Lambda",
      "Effect": "Allow",
      "Action": [
        "lambda:CreateFunction", "lambda:UpdateFunctionCode", "lambda:UpdateFunctionConfiguration",
        "lambda:GetFunction", "lambda:GetFunctionConfiguration", "lambda:DeleteFunction",
        "lambda:ListVersionsByFunction", "lambda:PublishVersion",
        "lambda:CreateAlias", "lambda:UpdateAlias", "lambda:GetAlias", "lambda:DeleteAlias",
        "lambda:AddPermission", "lambda:RemovePermission", "lambda:GetPolicy",
        "lambda:PutFunctionEventInvokeConfig", "lambda:GetFunctionEventInvokeConfig",
        "lambda:ListTags", "lambda:TagResource", "lambda:UntagResource",
        "lambda:PutFunctionConcurrency", "lambda:GetFunctionConcurrency",
        "lambda:DeleteFunctionConcurrency", "lambda:PutFunctionCodeSigningConfig",
        "lambda:GetFunctionCodeSigningConfig"
      ],
      "Resource": "arn:aws:lambda:${REGION}:${ACCOUNT_ID}:function:sudoku*"
    },
    {
      "Sid": "ApiGateway",
      "Effect": "Allow",
      "Action": "apigateway:*",
      "Resource": "arn:aws:apigateway:${REGION}::*"
    },
    {
      "Sid": "Amplify",
      "Effect": "Allow",
      "Action": "amplify:*",
      "Resource": "arn:aws:amplify:${REGION}:${ACCOUNT_ID}:apps/*"
    },
    {
      "Sid": "IAMLambdaRole",
      "Effect": "Allow",
      "Action": [
        "iam:CreateRole", "iam:GetRole", "iam:DeleteRole",
        "iam:AttachRolePolicy", "iam:DetachRolePolicy",
        "iam:CreatePolicy", "iam:GetPolicy", "iam:DeletePolicy",
        "iam:GetPolicyVersion", "iam:ListPolicyVersions",
        "iam:ListAttachedRolePolicies", "iam:ListRolePolicies",
        "iam:ListInstanceProfilesForRole",
        "iam:PassRole", "iam:TagRole", "iam:UntagRole",
        "iam:TagPolicy", "iam:UntagPolicy"
      ],
      "Resource": [
        "arn:aws:iam::${ACCOUNT_ID}:role/SudokuLambdaExecRole*",
        "arn:aws:iam::${ACCOUNT_ID}:policy/SudokuDynamoDBPolicy*",
        "arn:aws:iam::${ACCOUNT_ID}:policy/SudokuPlayersPolicy*"
      ]
    },
    {
      "Sid": "CognitoUserPool",
      "Effect": "Allow",
      "Action": ["cognito-idp:*"],
      "Resource": ["arn:aws:cognito-idp:${REGION}:${ACCOUNT_ID}:userpool/*"]
    },
    {
      "Sid": "CognitoDomain",
      "Effect": "Allow",
      "Action": [
        "cognito-idp:CreateUserPoolDomain",
        "cognito-idp:DeleteUserPoolDomain",
        "cognito-idp:DescribeUserPoolDomain",
        "cognito-idp:UpdateUserPoolDomain"
      ],
      "Resource": "*"
    },
    {
      "Sid": "DynamoDB",
      "Effect": "Allow",
      "Action": [
        "dynamodb:CreateTable", "dynamodb:DescribeTable", "dynamodb:DeleteTable",
        "dynamodb:UpdateTable", "dynamodb:ListTagsOfResource",
        "dynamodb:TagResource", "dynamodb:UntagResource",
        "dynamodb:DescribeContinuousBackups", "dynamodb:UpdateContinuousBackups",
        "dynamodb:DescribeTimeToLive"
      ],
      "Resource": [
        "arn:aws:dynamodb:${REGION}:${ACCOUNT_ID}:table/SudokuGames*",
        "arn:aws:dynamodb:${REGION}:${ACCOUNT_ID}:table/SudokuPlayers*"
      ]
    },
    {
      "Sid": "LambdaZipBucket",
      "Effect": "Allow",
      "Action": [
        "s3:CreateBucket", "s3:DeleteBucket", "s3:GetBucketAcl",
        "s3:GetBucketCORS", "s3:GetBucketLocation", "s3:GetBucketLogging",
        "s3:GetBucketObjectLockConfiguration", "s3:GetBucketPolicy",
        "s3:GetBucketPolicyStatus", "s3:GetBucketPublicAccessBlock",
        "s3:GetBucketRequestPayment", "s3:GetBucketTagging",
        "s3:GetBucketVersioning", "s3:GetBucketWebsite",
        "s3:GetAccelerateConfiguration", "s3:GetBucketAcl",
        "s3:GetBucketCORS", "s3:GetBucketLocation", "s3:GetBucketLogging",
        "s3:GetBucketNotification", "s3:GetBucketObjectLockConfiguration",
        "s3:GetBucketPolicy", "s3:GetBucketPolicyStatus",
        "s3:GetBucketPublicAccessBlock", "s3:GetBucketRequestPayment",
        "s3:GetBucketTagging", "s3:GetBucketVersioning", "s3:GetBucketWebsite",
        "s3:GetEncryptionConfiguration", "s3:GetLifecycleConfiguration",
        "s3:GetObject", "s3:GetReplicationConfiguration",
        "s3:ListBucket", "s3:PutObject", "s3:DeleteObject",
        "s3:PutObjectTagging", "s3:GetObjectTagging", "s3:DeleteObjectTagging",
        "s3:PutBucketPublicAccessBlock", "s3:PutBucketVersioning",
        "s3:PutLifecycleConfiguration", "s3:PutBucketTagging",
        "s3:PutBucketOwnershipControls", "s3:PutEncryptionConfiguration"
      ],
      "Resource": [
        "arn:aws:s3:::sudoku-lambda-zip-${ACCOUNT_ID}",
        "arn:aws:s3:::sudoku-lambda-zip-${ACCOUNT_ID}/*"
      ]
    },
    {
      "Sid": "CloudWatchLogs",
      "Effect": "Allow",
      "Action": [
        "logs:CreateLogGroup", "logs:DeleteLogGroup",
        "logs:PutRetentionPolicy", "logs:DescribeLogGroups",
        "logs:ListTagsLogGroup", "logs:TagLogGroup", "logs:UntagLogGroup",
        "logs:ListTagsForResource", "logs:TagResource", "logs:UntagResource"
      ],
      "Resource": "arn:aws:logs:${REGION}:${ACCOUNT_ID}:log-group:*"
    },
    {
      "Sid": "CloudWatchLogDelivery",
      "Effect": "Allow",
      "Action": [
        "logs:CreateLogDelivery", "logs:DeleteLogDelivery",
        "logs:GetLogDelivery", "logs:UpdateLogDelivery", "logs:ListLogDeliveries",
        "logs:PutResourcePolicy", "logs:DescribeResourcePolicies"
      ],
      "Resource": "*"
    }
  ]
}
EOF
)

ROLE_ARN=""
if aws iam get-role --role-name "${DEPLOY_ROLE_NAME}" 2>/dev/null; then
  echo "    Role already exists — updating trust policy."
  aws iam update-assume-role-policy \
    --role-name "${DEPLOY_ROLE_NAME}" \
    --policy-document "${TRUST_POLICY}"
  ROLE_ARN=$(aws iam get-role --role-name "${DEPLOY_ROLE_NAME}" --query Role.Arn --output text)
else
  ROLE_ARN=$(aws iam create-role \
    --role-name "${DEPLOY_ROLE_NAME}" \
    --assume-role-policy-document "${TRUST_POLICY}" \
    --query Role.Arn \
    --output text)
  echo "    Created."
fi

aws iam put-role-policy \
  --role-name "${DEPLOY_ROLE_NAME}" \
  --policy-name "SudokuDeployPolicy" \
  --policy-document "${DEPLOY_POLICY}"

echo "    Inline policy updated."

# ── Summary ─────────────────────────────────────────────────────────────────────
echo ""
echo "========================================================================"
echo "  Bootstrap complete."
echo "========================================================================"
echo ""
echo "  Role ARN: ${ROLE_ARN}"
echo ""
echo "  Next steps:"
echo "  1. Add the following GitHub Actions secret:"
echo "       AWS_DEPLOY_ROLE_ARN = ${ROLE_ARN}"
echo ""
echo "  2. Create a GitHub classic OAuth token (scopes: repo) and add:"
echo "       AMPLIFY_GITHUB_TOKEN = <your-token>"
echo ""
echo "  3. Add the following GitHub Actions secrets for Google social login:"
echo "       GOOGLE_CLIENT_ID     = <your-google-oauth-client-id>"
echo "       GOOGLE_CLIENT_SECRET = <your-google-oauth-client-secret>"
echo ""
echo "  4. The backend S3 block is already in infra/terraform.tf."
echo "     Run: cd infra && terraform init"
echo ""
echo "  5. If SudokuGames DynamoDB table already exists in AWS, import it:"
echo "       terraform import aws_dynamodb_table.sudoku_games SudokuGames"
echo ""
echo "  6. Push to main to trigger the deploy workflow."
echo "========================================================================"
