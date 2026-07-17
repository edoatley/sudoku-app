resource "aws_iam_role" "lambda_exec" {
  name = "SudokuLambdaExecRole${local.suffix}"

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

resource "aws_iam_role_policy_attachment" "lambda_basic_execution" {
  role       = aws_iam_role.lambda_exec.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_policy" "sudoku_dynamodb" {
  name        = "SudokuDynamoDBPolicy${local.suffix}"
  description = "Grants the Sudoku Lambda DynamoDB access to the SudokuGames table; Scan is used by the admin data browser"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "dynamodb:GetItem",
          "dynamodb:PutItem",
          "dynamodb:UpdateItem",
          "dynamodb:Query",
          "dynamodb:Scan"
        ]
        Resource = aws_dynamodb_table.sudoku_games.arn
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "sudoku_dynamodb" {
  role       = aws_iam_role.lambda_exec.name
  policy_arn = aws_iam_policy.sudoku_dynamodb.arn
}

resource "aws_iam_policy" "sudoku_players_dynamodb" {
  name        = "SudokuPlayersPolicy${local.suffix}"
  description = "Grants the Sudoku Lambda DynamoDB access to the SudokuPlayers table; Scan is used by the admin data browser"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "dynamodb:GetItem",
          "dynamodb:PutItem",
          "dynamodb:UpdateItem",
          "dynamodb:Scan"
        ]
        Resource = aws_dynamodb_table.sudoku_players.arn
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "sudoku_players_dynamodb" {
  role       = aws_iam_role.lambda_exec.name
  policy_arn = aws_iam_policy.sudoku_players_dynamodb.arn
}

resource "aws_iam_policy" "sudoku_leaderboard_dynamodb" {
  name        = "SudokuLeaderboardPolicy${local.suffix}"
  description = "Grants the Sudoku Lambda DynamoDB access to the SudokuLeaderboard table"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "dynamodb:GetItem",
          "dynamodb:UpdateItem",
          "dynamodb:Scan"
        ]
        Resource = aws_dynamodb_table.sudoku_leaderboard.arn
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "sudoku_leaderboard_dynamodb" {
  role       = aws_iam_role.lambda_exec.name
  policy_arn = aws_iam_policy.sudoku_leaderboard_dynamodb.arn
}

resource "aws_iam_policy" "sudoku_coach_rate_limits_dynamodb" {
  name        = "SudokuCoachRateLimitsPolicy${local.suffix}"
  description = "Grants the Sudoku Lambda DynamoDB access to the per-user coach rate limit table"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "dynamodb:GetItem",
          "dynamodb:UpdateItem"
        ]
        Resource = aws_dynamodb_table.sudoku_coach_rate_limits.arn
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "sudoku_coach_rate_limits_dynamodb" {
  role       = aws_iam_role.lambda_exec.name
  policy_arn = aws_iam_policy.sudoku_coach_rate_limits_dynamodb.arn
}

resource "aws_iam_policy" "sudoku_coach_bedrock" {
  name        = "SudokuCoachBedrockPolicy${local.suffix}"
  description = "Grants the Sudoku Lambda permission to invoke Bedrock for the AI coaching feature"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["bedrock:InvokeModel"]
        Resource = local.bedrock_invoke_resources
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "sudoku_coach_bedrock" {
  role       = aws_iam_role.lambda_exec.name
  policy_arn = aws_iam_policy.sudoku_coach_bedrock.arn
}
