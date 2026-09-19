"""The two Cloud Run environments, and the asymmetry between them.

Every value here is consumed by application code that fails at *runtime* when it is wrong — a
missing CORS origin blocks the browser, a wrong issuer 401s every request, and a `GCP_REGION` on
the image-recognition service 404s every recognition. None of it surfaces at plan time, which is
why it is asserted here.

@spec CP-GCP-010, CP-GCP-011, CP-GCP-012, CP-GCP-089, CP-PUL-020
"""

from __future__ import annotations

import pytest

from components.service_env import backend_env, image_recognition_env

PROJECT = "sudoku-eo-2026"
REGION = "us-central1"
CORS = "https://sudoku.gcp.edoatley.co.uk,http://localhost:5173"


@pytest.fixture
def backend():
    return backend_env(project=PROJECT, region=REGION, cors_origins=CORS)


@pytest.fixture
def image_recognition():
    return image_recognition_env(project=PROJECT, cors_origins=CORS)


class TestBackend:
    def test_activates_the_gcp_profile_over_prod(self, backend):
        # %gcp supplies Firestore persistence and in-app JWT validation; %prod supplies the email
        # allow-list. Either alone is a different application.
        assert backend["QUARKUS_PROFILE"] == "gcp"
        assert backend["QUARKUS_CONFIG_PROFILE_PARENT"] == "prod"

    def test_both_project_id_variables_are_set(self, backend):
        # GCP_PROJECT_ID interpolates the OIDC issuer/audience; QUARKUS_GOOGLE_CLOUD_PROJECT_ID is
        # the Firestore client's. They are read by different code and both are required.
        assert backend["GCP_PROJECT_ID"] == PROJECT
        assert backend["QUARKUS_GOOGLE_CLOUD_PROJECT_ID"] == PROJECT

    def test_coach_runs_on_vertex(self, backend):
        assert backend["COACH_AI_PROVIDER"] == "vertex"

    def test_region_tracks_the_cloud_run_region(self, backend):
        # coach.vertex.location reads this. gemini-2.5-flash-lite is regional, so it must follow
        # the deployment region rather than the property's own default.
        assert backend["GCP_REGION"] == REGION

    def test_cors_origins_are_passed_through(self, backend):
        assert backend["CORS_ALLOWED_ORIGINS"] == CORS

    def test_env_is_exactly_the_documented_set(self, backend):
        assert set(backend) == {
            "QUARKUS_PROFILE",
            "QUARKUS_CONFIG_PROFILE_PARENT",
            "GCP_PROJECT_ID",
            "QUARKUS_GOOGLE_CLOUD_PROJECT_ID",
            "CORS_ALLOWED_ORIGINS",
            "COACH_AI_PROVIDER",
            "GCP_REGION",
        }


class TestImageRecognition:
    def test_selects_the_vertex_provider(self, image_recognition):
        assert image_recognition["IMAGE_AI_PROVIDER"] == "vertex"

    def test_gcp_region_is_never_set(self, image_recognition):
        # THE TRAP. providers/vertex.py reads GCP_REGION as the Vertex endpoint location,
        # defaulting to `global`. gemini-3.8-flash is served only from `global`; a regional
        # endpoint 404s with a message blaming the model name, so the failure reads as a naming
        # or entitlement fault rather than a location one.
        assert "GCP_REGION" not in image_recognition

    def test_takes_no_region_argument_at_all(self):
        # Stronger than the assertion above: the mistake is not available to make.
        with pytest.raises(TypeError):
            image_recognition_env(project=PROJECT, cors_origins=CORS, region=REGION)

    def test_model_is_not_pinned_in_infrastructure(self, image_recognition):
        # providers/vertex.py owns the model; its default is the value the accuracy gate cleared.
        # Pinning it here would be a second place to change.
        assert "VERTEX_MODELS" not in image_recognition

    def test_env_is_exactly_the_documented_set(self, image_recognition):
        assert set(image_recognition) == {
            "GCP_PROJECT_ID",
            "CORS_ALLOWED_ORIGINS",
            "IMAGE_AI_PROVIDER",
        }


class TestNoAwsAnywhere:
    """The point of the whole re-platform: the GCP project ends with zero long-lived credentials.

    @spec CP-GCP-089
    """

    @pytest.mark.parametrize("env_name", ["backend", "image_recognition"])
    def test_no_aws_or_bedrock_variables(self, env_name, request):
        env = request.getfixturevalue(env_name)
        assert not [k for k in env if "AWS" in k or "BEDROCK" in k], env
