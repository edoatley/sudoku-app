# @spec CP-INFRA-050, CP-INFRA-051, CP-INFRA-052
resource "aws_dynamodb_table" "sudoku_games" {
  name                        = "SudokuGames${local.suffix}"
  billing_mode                = "PAY_PER_REQUEST"
  hash_key                    = "userId"
  range_key                   = "gameId"
  deletion_protection_enabled = local.is_default

  attribute {
    name = "userId"
    type = "S"
  }

  attribute {
    name = "gameId"
    type = "S"
  }

  point_in_time_recovery {
    enabled = local.is_default
  }

  # checkov:skip=CKV_AWS_119: KMS CMK encryption costs ~$1/month; AWS-owned encryption (default) is sufficient for this project
  # checkov:skip=CKV_AWS_28: PITR is enabled for production (local.is_default=true); disabled in RC/dev to avoid costs
}

# @spec CP-INFRA-050, CP-INFRA-051, CP-INFRA-052
resource "aws_dynamodb_table" "sudoku_leaderboard" {
  name                        = "SudokuLeaderboard${local.suffix}"
  billing_mode                = "PAY_PER_REQUEST"
  hash_key                    = "userId"
  deletion_protection_enabled = local.is_default

  attribute {
    name = "userId"
    type = "S"
  }

  point_in_time_recovery {
    enabled = local.is_default
  }

  # checkov:skip=CKV_AWS_119: KMS CMK encryption costs ~$1/month; AWS-owned encryption (default) is sufficient for this project
  # checkov:skip=CKV_AWS_28: PITR is enabled for production (local.is_default=true); disabled in RC/dev to avoid costs
}

# @spec CP-INFRA-050, CP-INFRA-051, CP-INFRA-052
resource "aws_dynamodb_table" "sudoku_players" {
  name                        = "SudokuPlayers${local.suffix}"
  billing_mode                = "PAY_PER_REQUEST"
  hash_key                    = "userId"
  deletion_protection_enabled = local.is_default

  attribute {
    name = "userId"
    type = "S"
  }

  point_in_time_recovery {
    enabled = local.is_default
  }

  # checkov:skip=CKV_AWS_119: KMS CMK encryption costs ~$1/month; AWS-owned encryption (default) is sufficient for this project
  # checkov:skip=CKV_AWS_28: PITR is enabled for production (local.is_default=true); disabled in RC/dev to avoid costs
}

# @spec CP-INFRA-050
resource "aws_dynamodb_table" "sudoku_coach_rate_limits" {
  name         = "SudokuCoachRateLimits${local.suffix}"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "userId"
  range_key    = "window"

  attribute {
    name = "userId"
    type = "S"
  }

  attribute {
    name = "window"
    type = "S"
  }

  ttl {
    attribute_name = "expiresAt"
    enabled        = true
  }

  point_in_time_recovery {
    enabled = false
  }

  # checkov:skip=CKV_AWS_119: KMS CMK encryption costs ~$1/month; AWS-owned encryption (default) is sufficient for this project
  # checkov:skip=CKV_AWS_28: PITR not needed for ephemeral rate-limit counters with TTL-based expiry
}
