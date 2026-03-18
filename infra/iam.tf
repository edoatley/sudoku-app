data "aws_iam_role" "lambda_exec" {
  name = var.lambda_exec_role_name
}

resource "aws_iam_policy" "sudoku_dynamodb" {
  name        = "SudokuDynamoDBPolicy"
  description = "Grants the Sudoku Lambda minimal DynamoDB access"

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
        Resource = aws_dynamodb_table.sudoku_games.arn
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "sudoku_dynamodb" {
  role       = data.aws_iam_role.lambda_exec.name
  policy_arn = aws_iam_policy.sudoku_dynamodb.arn
}
