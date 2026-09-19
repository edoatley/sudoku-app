"""The environment each Cloud Run service receives.

Kept apart from the stack program so the values can be asserted on directly. Every one of them
fails at *runtime* rather than at plan time when it is wrong — a wrong issuer 401s every request,
a missing CORS origin blocks the browser, and a stray `GCP_REGION` 404s every image recognition —
so a plan-time green is no evidence at all here.

@spec CP-GCP-010, CP-GCP-011, CP-GCP-012, CP-GCP-089, CP-PUL-020
"""

from __future__ import annotations

import pulumi


def backend_env(
    *,
    project: pulumi.Input[str],
    region: str,
    cors_origins: pulumi.Input[str],
) -> dict[str, pulumi.Input[str]]:
    """Environment for the Quarkus backend.

    ``QUARKUS_PROFILE=gcp`` supplies Firestore persistence and in-app Firebase JWT validation;
    ``%prod`` as its parent supplies the email allow-list. The two project-id variables are read
    by different code — OIDC issuer/audience interpolation and the Firestore client respectively —
    and both are required.
    """
    return {
        "QUARKUS_PROFILE": "gcp",
        "QUARKUS_CONFIG_PROFILE_PARENT": "prod",
        "GCP_PROJECT_ID": project,
        "QUARKUS_GOOGLE_CLOUD_PROJECT_ID": project,
        "CORS_ALLOWED_ORIGINS": cors_origins,
        "COACH_AI_PROVIDER": "vertex",
        # coach.vertex.location reads this. gemini-2.5-flash-lite is regional, so it tracks the
        # Cloud Run region rather than the property's own default — a non-default region would
        # otherwise silently mismatch.
        "GCP_REGION": region,
    }


def image_recognition_env(
    *,
    project: pulumi.Input[str],
    cors_origins: pulumi.Input[str],
) -> dict[str, pulumi.Input[str]]:
    """Environment for the image-recognition service.

    **There is deliberately no region parameter.** `providers/vertex.py` reads ``GCP_REGION`` as
    the Vertex endpoint location, and `gemini-3.8-flash` is served only from the ``global``
    endpoint — which is exactly what the provider falls back to when the variable is absent. A
    regional value returns a 404 whose message suggests the model does not exist or is not
    permitted, so the fault reads as a naming or entitlement problem rather than a location one.
    The parameter is omitted so the mistake cannot be made, not merely caught.

    The model stays unpinned here: ``VERTEX_MODELS`` is an override, and the default in
    `providers/vertex.py` is the value the accuracy gate cleared.
    """
    return {
        "GCP_PROJECT_ID": project,
        "CORS_ALLOWED_ORIGINS": cors_origins,
        "IMAGE_AI_PROVIDER": "vertex",
    }
