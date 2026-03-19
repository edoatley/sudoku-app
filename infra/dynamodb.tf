resource "aws_dynamodb_table" "sudoku_games" {
  name         = "SudokuGames"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "gameId"

  attribute {
    name = "gameId"
    type = "S"
  }

  tags = {
    Project   = "Sudoku"
    ManagedBy = "Terraform"
  }
}
