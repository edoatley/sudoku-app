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
  description = "Grants the Sudoku Lambda minimal DynamoDB access"

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
  description = "Grants the Sudoku Lambda minimal DynamoDB access to the SudokuPlayers table"

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

resource "aws_iam_policy" "sudoku_coach_bedrock" {
  name        = "SudokuCoachBedrockPolicy${local.suffix}"
  description = "Grants the Sudoku Lambda permission to invoke Bedrock for the AI coaching feature"

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

resource "aws_iam_role_policy_attachment" "sudoku_coach_bedrock" {
  role       = aws_iam_role.lambda_exec.name
  policy_arn = aws_iam_policy.sudoku_coach_bedrock.arn
}
