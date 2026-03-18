.PHONY: dev dev-backend dev-ui localstack-up localstack-table

LOCALSTACK_ENDPOINT=http://localhost:4566
AWS=aws --endpoint-url=$(LOCALSTACK_ENDPOINT) --region eu-west-2

localstack-up:
	@echo "Starting LocalStack ..."
	docker run --rm -d --name sudoku-localstack \
	  -p 4566:4566 \
	  -e SERVICES=dynamodb \
	  localstack/localstack
	@echo "Waiting for LocalStack to be ready ..."
	@until $(AWS) dynamodb list-tables >/dev/null 2>&1; do sleep 1; done
	@echo "LocalStack ready."

localstack-table:
	@echo "Creating SudokuGames table ..."
	@$(AWS) dynamodb create-table \
	  --table-name SudokuGames \
	  --attribute-definitions AttributeName=gameId,AttributeType=S \
	  --key-schema AttributeName=gameId,KeyType=HASH \
	  --billing-mode PAY_PER_REQUEST \
	  --output text >/dev/null 2>&1 || echo "Table already exists, skipping."

dev-backend:
	cd backend && ./mvnw quarkus:dev

dev-ui:
	cd ui && npm run dev

dev: localstack-up localstack-table
	@echo "Starting backend (port 8080) and frontend (port 5173) ..."
	@trap 'kill 0; docker stop sudoku-localstack 2>/dev/null' SIGINT; \
	  (cd backend && ./mvnw quarkus:dev) & \
	  (cd ui && npm run dev) & \
	  wait
