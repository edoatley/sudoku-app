# Route53 hosted zone for the delegated subdomain.
# Created once in the default workspace only.
# After apply, copy the name_servers output and run infra/scripts/delegate-dns.sh
# in the parent AWS account to complete the delegation.
resource "aws_route53_zone" "sudoku" {
  count   = local.is_default ? 1 : 0
  name    = "sudoku.edoatley.co.uk"
  comment = "Delegated zone for the Sudoku Amplify app"
}

# RC workspaces read the zone ID from the default workspace's remote state
# rather than doing a live lookup — avoids a ListHostedZones dependency on
# the zone existing before this workspace is first applied.
data "terraform_remote_state" "default" {
  count   = local.is_rc ? 1 : 0
  backend = "s3"
  config = {
    bucket = "sudoku-tf-state"
    key    = "sudoku/terraform.tfstate"
    region = "eu-west-2"
  }
}

# Production domain association — default workspace only.
# Maps sudoku.edoatley.co.uk and www.sudoku.edoatley.co.uk → main branch.
# Amplify provisions the ACM certificate automatically.
resource "aws_amplify_domain_association" "production" {
  count       = local.is_default ? 1 : 0
  app_id      = aws_amplify_app.sudoku.id
  domain_name = "sudoku.edoatley.co.uk"

  sub_domain {
    branch_name = aws_amplify_branch.main.branch_name
    prefix      = ""
  }

  sub_domain {
    branch_name = aws_amplify_branch.main.branch_name
    prefix      = "www"
  }

  # ACM certificate provisioning can take up to 40 minutes on first apply.
  timeouts {
    create = "60m"
    update = "60m"
  }
}

# Beta domain association — rc-* workspaces only.
# Maps sudoku-beta.edoatley.co.uk → the current RC branch.
# Only one RC branch holds this domain at a time (last writer wins).
# Prerequisite: default workspace must have been applied first (zone must exist).
resource "aws_amplify_domain_association" "beta" {
  count       = local.is_rc && length(data.terraform_remote_state.default) > 0 && data.terraform_remote_state.default[0].outputs.route53_zone_id != null ? 1 : 0
  app_id      = aws_amplify_app.sudoku.id
  domain_name = "sudoku-beta.edoatley.co.uk"

  sub_domain {
    branch_name = aws_amplify_branch.main.branch_name
    prefix      = ""
  }

  # ACM certificate provisioning can take up to 40 minutes on first apply.
  timeouts {
    create = "60m"
    update = "60m"
  }
}
