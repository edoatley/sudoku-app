"""A Cloud Run service and its public invoker binding.

Instantiated twice — backend and image recognition — from one class. Replaces `cloud_run.tf`
(158 lines) and `image_recognition.tf` (129 lines), which are roughly 70% identical.
@spec CP-GCP-001, CP-GCP-002, CP-GCP-003, CP-GCP-004, CP-GCP-013, CP-PUL-020, CP-PUL-023
"""

from __future__ import annotations

from collections.abc import Mapping

import pulumi
import pulumi_gcp as gcp

DEFAULT_PORT = 8080
DEFAULT_CPU = "1"
DEFAULT_MEMORY = "512Mi"
DEFAULT_TIMEOUT = "60s"
"""8s was too tight: a cold JVM boot (min instances 0) can exceed it, 504-ing the first request
after idle, and it left no headroom for the coach's ~6s model call."""


class ContainerService(pulumi.ComponentResource):
    """A scale-to-zero Cloud Run service running as its own service account.

    GCP has no API-Gateway-style request-rate throttle, so spend and load are bounded by
    ``max_instances`` x ``concurrency`` plus the per-request timeout. This is the documented,
    accepted substitute (`CP-GCP-013`).
    """

    def __init__(
        self,
        name: str,
        *,
        project: pulumi.Input[str],
        location: str,
        service_name: pulumi.Input[str],
        image: pulumi.Input[str],
        service_account_email: pulumi.Input[str],
        env: Mapping[str, pulumi.Input[str]],
        max_instances: int,
        concurrency: int,
        port: int = DEFAULT_PORT,
        cpu: str = DEFAULT_CPU,
        memory: str = DEFAULT_MEMORY,
        timeout: str = DEFAULT_TIMEOUT,
        public: bool = True,
        deletion_protection: bool = False,
        opts: pulumi.ResourceOptions | None = None,
    ) -> None:
        super().__init__("sudoku:gcp:ContainerService", name, None, opts)
        child = pulumi.ResourceOptions(parent=self)

        self.service = gcp.cloudrunv2.Service(
            f"{name}-service",
            project=project,
            location=location,
            name=service_name,
            ingress="INGRESS_TRAFFIC_ALL",
            deletion_protection=deletion_protection,
            template=gcp.cloudrunv2.ServiceTemplateArgs(
                service_account=service_account_email,
                timeout=timeout,
                max_instance_request_concurrency=concurrency,
                scaling=gcp.cloudrunv2.ServiceTemplateScalingArgs(
                    min_instance_count=0,
                    max_instance_count=max_instances,
                ),
                containers=[
                    gcp.cloudrunv2.ServiceTemplateContainerArgs(
                        image=image,
                        ports=gcp.cloudrunv2.ServiceTemplateContainerPortsArgs(container_port=port),
                        resources=gcp.cloudrunv2.ServiceTemplateContainerResourcesArgs(
                            limits={"cpu": cpu, "memory": memory},
                            cpu_idle=True,
                        ),
                        envs=[
                            gcp.cloudrunv2.ServiceTemplateContainerEnvArgs(name=k, value=v)
                            for k, v in sorted(env.items())
                        ],
                    )
                ],
            ),
            opts=child,
        )

        # Granted on EVERY stack including prod, closing gap G1 and deleting
        # scripts/infra/gcp/grant-prod-invoker.sh. Terraform withheld it in prod so an apply
        # could not toggle production reachability — but the app stack already holds
        # roles/run.admin and can delete the service outright, so withholding one additive
        # binding buys nothing and costs a manual step on every recreation.
        #
        # This grants NETWORK REACHABILITY ONLY. Each service still validates the caller's JWT
        # in-app; on GCP that in-app check is the sole auth gate, there being no gateway.
        self.public_invoker = None
        if public:
            self.public_invoker = gcp.cloudrunv2.ServiceIamMember(
                f"{name}-public-invoker",
                project=project,
                location=location,
                name=self.service.name,
                role="roles/run.invoker",
                member="allUsers",
                opts=child,
            )

        self.url: pulumi.Output[str] = self.service.uri
        self.service_name: pulumi.Output[str] = self.service.name
        self.latest_ready_revision: pulumi.Output[str] = self.service.latest_ready_revision

        self.register_outputs(
            {
                "url": self.url,
                "service_name": self.service_name,
                "latest_ready_revision": self.latest_ready_revision,
            }
        )
