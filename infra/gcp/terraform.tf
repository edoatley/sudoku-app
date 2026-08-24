terraform {
  required_version = ">= 1.10"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 6.0"
    }
    google-beta = {
      source  = "hashicorp/google-beta"
      version = "~> 6.0"
    }
  }

  backend "gcs" {
    bucket = "sudoku-tf-state-gcp"
    prefix = "sudoku"
  }
}

# @spec CP-GCP-070
provider "google" {
  project = var.project_id
  region  = var.region

  default_labels = {
    project     = "sudoku"
    managed_by  = "terraform"
    environment = local.environment_label
  }
}

provider "google-beta" {
  project = var.project_id
  region  = var.region

  default_labels = {
    project     = "sudoku"
    managed_by  = "terraform"
    environment = local.environment_label
  }
}
