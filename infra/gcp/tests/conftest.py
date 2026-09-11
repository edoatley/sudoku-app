"""Pulumi mock runtime, so components can be instantiated and asserted on with no cloud contact.

This is the seam HCL never offered, and it is why the GCP facet gets an infrastructure test layer
where the AWS facet still records "no Terratest or equivalent" as an accepted gap.
@spec CP-PUL-081
"""

from __future__ import annotations

from typing import Any

import pulumi


class _Mocks(pulumi.runtime.Mocks):
    def new_resource(self, args: pulumi.runtime.MockResourceArgs) -> tuple[str, dict]:
        outputs: dict[str, Any] = dict(args.inputs)
        # Server-assigned fields the components compose outputs from. Without these, the
        # Output.concat chains under test resolve to None and the assertions become vacuous.
        if args.typ == "gcp:cloudrunv2/service:Service":
            outputs["uri"] = f"https://{args.name}-abc123.a.run.app"
            outputs["latestReadyRevision"] = f"{args.name}-00001"
        elif args.typ == "gcp:organizations/project:Project":
            outputs["number"] = "123456789012"
        elif args.typ == "gcp:kms/cryptoKey:CryptoKey":
            # Pulumi uses the *returned* id, not outputs["id"] — return a realistic one so the
            # gcp-kms:// secrets-provider URI under test is the shape a real key produces.
            key = args.inputs.get("name", args.name)
            return (
                f"projects/test-project/locations/us-central1/keyRings/sudoku-pulumi/"
                f"cryptoKeys/{key}",
                outputs,
            )
        elif args.typ == "gcp:iam/workloadIdentityPool:WorkloadIdentityPool":
            pool = args.inputs.get("workloadIdentityPoolId", args.name)
            outputs["name"] = f"projects/123456789012/locations/global/workloadIdentityPools/{pool}"
        elif args.typ == "gcp:dns/managedZone:ManagedZone":
            outputs["nameServers"] = [f"ns-cloud-a{i}.googledomains.com." for i in range(1, 5)]
        elif args.typ == "gcp:serviceaccount/account:Account":
            account_id = args.inputs.get("accountId", args.name)
            outputs["email"] = f"{account_id}@test-project.iam.gserviceaccount.com"
        return f"{args.name}_id", outputs

    def call(self, args: pulumi.runtime.MockCallArgs) -> tuple[dict, list | None]:
        return {}, None


pulumi.runtime.set_mocks(_Mocks(), project="sudoku-test", stack="test", preview=False)
