"""Tests for the Cloud Run HTTP front (app.py). The recognition core (handler.py) is covered by
test_unit.py; here we test HTTP wiring, auth gating, and CORS.

@spec IR-GCP-001, IR-GCP-002, IR-GCP-003, IR-GCP-004
"""

import importlib
import json

from fastapi.testclient import TestClient

import app as appmod
from app import app, verify_token

client = TestClient(app)


def test_warmup_is_open_and_never_calls_bedrock():
    # No Authorization header — warmup must be reachable pre-login.
    r = client.get("/ai/image-to-puzzle/warmup")
    assert r.status_code == 200
    assert r.json()["status"] == "ok"


def test_post_without_token_is_rejected(monkeypatch):
    # PROJECT_ID set so we reach the bearer-token check (not the 500 config guard).
    monkeypatch.setattr(appmod, "PROJECT_ID", "sudoku-test")
    r = client.post("/ai/image-to-puzzle", json={"image": "x"})
    assert r.status_code == 401


def test_post_without_project_id_is_misconfigured():
    # PROJECT_ID defaults to "" in tests → auth is unconfigured → 500 rather than open access.
    r = client.post("/ai/image-to-puzzle", json={"image": "x"})
    assert r.status_code == 500


def test_post_with_valid_token_delegates_to_recognition(monkeypatch):
    app.dependency_overrides[verify_token] = lambda: {"email": "bob@gmail.com"}
    monkeypatch.setattr(
        appmod.recognition,
        "handler",
        lambda event, ctx: {
            "statusCode": 200,
            "body": json.dumps(
                {"originalGrid": [[0] * 9] * 9, "validPuzzle": True, "modelName": "m"}
            ),
        },
    )
    try:
        r = client.post("/ai/image-to-puzzle", json={"image": "abc"})
        assert r.status_code == 200
        body = r.json()
        assert body["validPuzzle"] is True
        assert len(body["originalGrid"]) == 9
    finally:
        app.dependency_overrides.clear()


def test_cors_preflight_allows_configured_origin(monkeypatch):
    # CORS origins are read at import time; set env and reload to exercise the middleware.
    monkeypatch.setenv("CORS_ALLOWED_ORIGINS", "https://example.web.app")
    reloaded = importlib.reload(appmod)
    try:
        c = TestClient(reloaded.app)
        r = c.options(
            "/ai/image-to-puzzle",
            headers={
                "Origin": "https://example.web.app",
                "Access-Control-Request-Method": "POST",
            },
        )
        assert r.status_code == 200
        assert r.headers.get("access-control-allow-origin") == "https://example.web.app"
    finally:
        monkeypatch.delenv("CORS_ALLOWED_ORIGINS", raising=False)
        importlib.reload(appmod)  # restore module state for other tests
