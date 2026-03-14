# Infrastructure Guidelines

**Stack:** Terraform, AWS

## Resource Definitions

- **File Structure:** Maintain a clean separation of concerns. Use `main.tf` (provider), `variables.tf` (inputs), `outputs.tf` (exports), `lambda.tf` (compute), and `api_gateway.tf` (routing).
- **API Gateway:** Strictly use **API Gateway v2 (HTTP API)** for cost and performance efficiency. Do not use REST API v1.
- **CORS:** Explicitly configure CORS on the HTTP API to allow `GET` and `POST` requests. In production, restrict the allowed origin to the AWS Amplify frontend URL. Allow `http://localhost:5173` for development.
- **Frontend Hosting:** Use AWS Amplify (`aws_amplify_app`, `aws_amplify_branch`) to host the React application directly from the GitHub repository.
- **Tagging:** Apply a default set of tags to every resource (e.g., `Project = "Sudoku"`, `ManagedBy = "Terraform"`) to ensure the AWS bill is easily trackable.
