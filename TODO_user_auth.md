# TODO: User Security Outline

## 1. User Authentication (Amazon Cognito)

Instead of building a custom login system (which is a security nightmare), you will provision an Amazon Cognito User Pool.

How it works for the UI: You use the @aws-amplify/auth library in your React app. It provides pre-built React components (<Authenticator>) that give you an instant Login/Signup/Forgot Password screen.

The Output: When a user logs in, Cognito returns a JWT (JSON Web Token) to the React frontend. This token securely identifies the user.

## 2. Granting the UI Access to the API (The Handshake)

Now that the UI has a token, it needs permission to talk to the backend. This requires two specific mechanisms:

A. CORS (Cross-Origin Resource Sharing)

By default, web browsers block your UI (e.g., sudoku.com) from making requests to a different domain (e.g., api.sudoku.com).

The Fix: You must configure your Terraform aws_apigatewayv2_api to explicitly trust your frontend.

Rule: Set allow_origins = ["https://your-amplify-domain.com"]. Never use ["*"] in production. Allow methods GET, POST, PATCH, OPTIONS.

B. API Gateway JWT Authorizer

You don't want your Java Lambda wasting time (and money) checking if a user is logged in. AWS can do this for free at the front door.

The Fix: You attach a JWT Authorizer to your API Gateway routes (like POST /games).

The Flow: 1. React sends a request with the header: Authorization: Bearer <Cognito_JWT>.
2. API Gateway intercepts it, verifies the cryptographic signature of the token with Cognito, and checks if it's expired.
3. If invalid, API Gateway blocks the request and returns a 401 Unauthorized (Lambda never fires = $0 cost).
4. If valid, API Gateway passes the request to your Java Lambda, injecting the user's ID (the sub claim) into the request context.

## 3. Data Isolation (The DynamoDB Vulnerability)

In our previous design, a user could theoretically guess another user's gameId and fetch their game via GET /games/{gameId}. This is an IDOR (Insecure Direct Object Reference) vulnerability.

The Fix: We must change the DynamoDB table design.

New Primary Key: The Partition Key (PK) should be the userId (provided by the Cognito JWT), and the Sort Key (SK) should be the gameId.

Lambda Logic: When your Java code queries the database, it doesn't just ask for gameId. It asks for "the game matching this gameId AND this userId." If User A requests User B's game, the database returns nothing.

## 4. Other Critical AWS Security Concerns

To make this production-ready and bulletproof against attacks (or accidental bills), implement these best practices in your Terraform code:

Rate Limiting (Throttling): On your API Gateway, set a strict Route level throttling limit. For example, limit requests to 10 per second per IP. This prevents a malicious script from hammering your generatePuzzle endpoint and racking up Lambda compute time.

Lambda IAM Role (Least Privilege): Your Java backend must have an execution role that is strictly confined.

Allowed: dynamodb:PutItem, dynamodb:GetItem strictly on arn:aws:dynamodb:REGION:ACCOUNT:table/SudokuGames.

Allowed: logs:CreateLogStream, logs:PutLogEvents.

Denied: Everything else.

Public vs. Private Endpoints: Not everything needs auth. You might want /puzzles/generate to remain public so guests can play one-off games, but /games/* requires the Cognito JWT Authorizer so users can save state.

## 📝 Updated Architecture Summary

React App -> Prompts user to log in via Cognito.

React App -> Sends Bearer Token to API Gateway.

API Gateway -> Validates token. Blocks bad traffic. Routes good traffic to Lambda.

Lambda (Java) -> Extracts the userId from the token. Generates board/validates logic.

Lambda (Java) -> Saves game to DynamoDB using userId as the master key.