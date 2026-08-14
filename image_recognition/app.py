"""
HTTP front for the Sudoku image-recognition service on Google Cloud Run.

The recognition logic lives in ``handler.py`` (the AWS Lambda entry point, unchanged). This module
wraps it in a small FastAPI app so the *same* logic runs as a plain HTTP server on Cloud Run:
each request is translated into the API-Gateway-proxy event shape ``handler.handler`` already
expects, and its ``{statusCode, body}`` result is translated back into an HTTP response.

Unlike AWS — where API Gateway validates the JWT and applies CORS before the Lambda runs — Cloud
Run has no edge, so this front validates the caller's Firebase (Identity Platform) token in-app and
applies CORS itself, mirroring the backend's %gcp behaviour. The POST endpoint is Bedrock-backed, so
leaving it unauthenticated would be a cost/abuse risk; the warmup probe is deliberately open (the
frontend calls it before sign-in to spin the service up).

@spec IR-GCP-001, IR-GCP-002, IR-GCP-003, IR-GCP-004
"""

from __future__ import annotations

import os

import jwt
from fastapi import Depends, FastAPI, HTTPException, Request, Response
from fastapi.middleware.cors import CORSMiddleware
from jwt import PyJWKClient

import handler as recognition

# Identity Platform / Firebase token facts. The Firebase project id equals the GCP project id, and
# is both the audience and the trailing segment of the issuer.
PROJECT_ID = os.environ.get("GCP_PROJECT_ID", "")
JWKS_URL = "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"
ISSUER = f"https://securetoken.google.com/{PROJECT_ID}"

CORS_ORIGINS = [
    o.strip()
    for o in os.environ.get("CORS_ALLOWED_ORIGINS", "").split(",")
    if o.strip()
]
# Optional email allow-list for parity with the backend (UM-BE-020); empty = allow any valid token.
ALLOWED_EMAILS = {
    e.strip().lower()
    for e in os.environ.get("ALLOWED_EMAILS", "").split(",")
    if e.strip()
}

app = FastAPI(title="sudoku-image-recognition")
app.add_middleware(
    CORSMiddleware,
    allow_origins=CORS_ORIGINS,
    allow_methods=["GET", "POST", "OPTIONS"],
    allow_headers=["Content-Type", "Authorization"],
    allow_credentials=False,
)

# Created lazily so the module imports (and tests run) without a project id or network access.
_jwks_client: PyJWKClient | None = None


def _jwks() -> PyJWKClient:
    global _jwks_client
    if _jwks_client is None:
        _jwks_client = PyJWKClient(JWKS_URL)
    return _jwks_client


def verify_token(request: Request) -> dict:
    """Validate the caller's Firebase ID token; return its claims or raise 401/403."""
    if not PROJECT_ID:
        raise HTTPException(
            status_code=500, detail="Auth not configured (GCP_PROJECT_ID unset)"
        )

    auth = request.headers.get("Authorization", "")
    if not auth.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Missing bearer token")
    token = auth[len("Bearer ") :]

    try:
        signing_key = _jwks().get_signing_key_from_jwt(token)
        claims = jwt.decode(
            token,
            signing_key.key,
            algorithms=["RS256"],
            audience=PROJECT_ID,
            issuer=ISSUER,
        )
    except jwt.PyJWTError as exc:
        raise HTTPException(status_code=401, detail="Invalid token") from exc

    # Firebase reports email_verified truthfully; require it (parity with UM-GCP-005).
    if claims.get("email_verified") is not True:
        raise HTTPException(status_code=403, detail="Email not verified")
    email = (claims.get("email") or "").lower()
    if ALLOWED_EMAILS and email not in ALLOWED_EMAILS:
        raise HTTPException(status_code=403, detail="Email not allowed")
    return claims


def _to_response(result: dict) -> Response:
    return Response(
        content=result.get("body", "{}"),
        status_code=result.get("statusCode", 200),
        media_type="application/json",
    )


@app.get("/ai/image-to-puzzle/warmup")
def warmup() -> Response:
    # Unauthenticated by design — best-effort cold-start warm-up; never touches Bedrock.
    return _to_response(
        recognition.handler({"rawPath": "/ai/image-to-puzzle/warmup"}, None)
    )


@app.post("/ai/image-to-puzzle")
async def image_to_puzzle(
    request: Request, _claims: dict = Depends(verify_token)
) -> Response:
    raw = await request.body()
    event = {
        "rawPath": "/ai/image-to-puzzle",
        "body": raw.decode("utf-8") if raw else "",
    }
    return _to_response(recognition.handler(event, None))
