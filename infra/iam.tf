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
          "dynamodb:Query"
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
          "dynamodb:UpdateItem"
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
