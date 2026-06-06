resource "aws_dynamodb_table" "sudoku_games" {
  name         = "SudokuGames${local.suffix}"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "userId"
  range_key    = "gameId"

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
}

resource "aws_dynamodb_table" "sudoku_leaderboard" {
  name         = "SudokuLeaderboard${local.suffix}"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "userId"

  attribute {
    name = "userId"
    type = "S"
  }

  point_in_time_recovery {
    enabled = local.is_default
  }

  # checkov:skip=CKV_AWS_119: KMS CMK encryption costs ~$1/month; AWS-owned encryption (default) is sufficient for this project
}

resource "aws_dynamodb_table" "sudoku_players" {
  name         = "SudokuPlayers${local.suffix}"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "userId"

  attribute {
    name = "userId"
    type = "S"
  }

  point_in_time_recovery {
    enabled = local.is_default
  }

  # checkov:skip=CKV_AWS_119: KMS CMK encryption costs ~$1/month; AWS-owned encryption (default) is sufficient for this project
}
