.PHONY: dev dev-backend dev-ui

dev-backend:
	cd backend && ./mvnw quarkus:dev

dev-ui:
	cd ui && npm run dev

dev:
	@echo "Starting backend (port 8080) and frontend (port 5173) ..."
	@trap 'kill 0' SIGINT; \
	  (cd backend && ./mvnw quarkus:dev) & \
	  (cd ui && npm run dev) & \
	  wait
