.PHONY: setup lint secure check-all

setup:
	@echo "==> Installing Homebrew tools..."
	brew install biome ruff trivy checkov terraform pre-commit
	@echo "==> Installing pre-commit hooks..."
	pre-commit install
	pre-commit install-hooks
	@echo "==> Setup complete. Run 'make lint' or 'make secure' to verify."

lint:
	@echo "==> Running Biome for UI..."
	cd ui && biome check . --write
	@echo "==> Running Ruff for Python Lambdas..."
	ruff check --fix image_recognition/
	ruff format image_recognition/
	@echo "==> Formatting Terraform..."
	cd infra && terraform fmt

secure:
	@echo "==> Scanning codebase for vulnerabilities with Trivy..."
	trivy fs . --skip-dirs .terraform
	@echo "==> Analyzing cloud infrastructure configurations with Checkov..."
	checkov -d infra/ --framework terraform

check-all: lint secure
	@echo "==> Running backend validation tests..."
	cd backend && ./mvnw verify -DskipITs=false
