"""A Cloud DNS managed zone and its records.

Reverses `CP-GCP-050`, which kept the GCP hostname as a CNAME in the AWS Route53 parent zone —
the last AWS dependency in the GCP serving path. That spec's rationale ("Cloud DNS has no
apex-alias equivalent") only bites at a zone apex; `sudoku.gcp.edoatley.co.uk` sits inside a
delegated `gcp.edoatley.co.uk` zone, where an ordinary CNAME works.
@spec CP-PUL-050
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass

import pulumi
import pulumi_gcp as gcp

DEFAULT_TTL = 300


@dataclass(frozen=True)
class RecordSpec:
    name: str  # fully qualified, trailing dot
    type: str
    rrdatas: tuple[str, ...]
    ttl: int = DEFAULT_TTL


class DnsZone(pulumi.ComponentResource):
    """A managed zone for a delegated subdomain, plus its records.

    The parent zone needs one NS record added by hand, once. After that every record below the
    delegation is Pulumi-managed and AWS is never touched.

    Cost note: a managed zone is ~$0.20/month — the first standing charge on the GCP side, and a
    deliberate, recorded exception to the free-tier tenet.
    """

    def __init__(
        self,
        name: str,
        *,
        project: pulumi.Input[str],
        zone_name: str,
        dns_name: str,
        records: Sequence[RecordSpec] = (),
        opts: pulumi.ResourceOptions | None = None,
    ) -> None:
        super().__init__("sudoku:gcp:DnsZone", name, None, opts)

        if not dns_name.endswith("."):
            raise ValueError(f"dns_name must be fully qualified with a trailing dot: {dns_name!r}")

        self.zone = gcp.dns.ManagedZone(
            f"{name}-zone",
            project=project,
            name=zone_name,
            dns_name=dns_name,
            description="Sudoku GCP deployment target",
            opts=pulumi.ResourceOptions.merge(
                opts, pulumi.ResourceOptions(parent=self, protect=True)
            ),
        )

        child = pulumi.ResourceOptions.merge(opts, pulumi.ResourceOptions(parent=self))
        self.records = [
            gcp.dns.RecordSet(
                f"{name}-rr-{spec.type.lower()}-{spec.name.rstrip('.').replace('.', '-')}",
                project=project,
                managed_zone=self.zone.name,
                name=spec.name,
                type=spec.type,
                ttl=spec.ttl,
                rrdatas=list(spec.rrdatas),
                opts=child,
            )
            for spec in records
        ]

        # Printed by the pipeline and read via `pulumi stack output name_servers` to create the
        # one-time NS delegation in the parent zone.
        self.name_servers: pulumi.Output[list[str]] = self.zone.name_servers

        self.register_outputs({"name_servers": self.name_servers})
