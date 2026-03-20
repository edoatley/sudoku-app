terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  backend "s3" {
    bucket         = "sudoku-tf-state"
    key            = "sudoku/terraform.tfstate"
    region         = "eu-west-2"
    dynamodb_table = "sudoku-tf-locks"
    encrypt        = true
  }
}

provider "aws" {
  region = "eu-west-2"

  default_tags {
    tags = {
      Project     = "Sudoku"
      ManagedBy   = "Terraform"
      Environment = var.environment
    }
  }
}
