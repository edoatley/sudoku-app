"""Sudoku GCP — application stack.

Firestore, the two Cloud Run services, Identity Platform, Firebase Hosting and Cloud DNS. Run by
CI as the Workload-Identity-federated deploy service account.

Reads the bootstrap stack's outputs by StackReference; every ephemeral `rcg-*` stack references
the same bootstrap stack.

Phase 1 note: this program is written but not yet run against any cloud. Phases 3, 4 and 6 fill
it in (data + hosting + DNS, then Identity Platform, then compute + frontend).
"""

from __future__ import annotations

import pulumi

config = pulumi.Config("sudoku")
stack = pulumi.get_stack()

# Placeholder wiring: later phases add
#   bootstrap = pulumi.StackReference(config.require("bootstrapStack"))
# then FirestoreDatabase, StaticSite, DnsZone, IdentityPlatform and ContainerService x2.
pulumi.log.info(f"app stack '{stack}' is scaffolded; resources land in Phases 3, 4 and 6")
