# Route53 hosted zone for the delegated subdomain.
# Created once in the default workspace only.
# After apply, copy the name_servers output and run infra/scripts/delegate-dns.sh
# in the parent AWS account to complete the delegation.
resource "aws_route53_zone" "sudoku" {
  count   = local.is_default ? 1 : 0
  name    = "sudoku.edoatley.co.uk"
  comment = "Delegated zone for the Sudoku Amplify app"
}

# RC workspaces reference the zone created by the default workspace.
# Prerequisite: the default workspace must have been applied first.
data "aws_route53_zone" "sudoku" {
  count = local.is_rc ? 1 : 0
  name  = "sudoku.edoatley.co.uk"
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
}

# Beta domain association — rc-* workspaces only.
# Maps sudoku-beta.edoatley.co.uk → the current RC branch.
# Only one RC branch holds this domain at a time (last writer wins).
resource "aws_amplify_domain_association" "beta" {
  count       = local.is_rc ? 1 : 0
  app_id      = aws_amplify_app.sudoku.id
  domain_name = "sudoku-beta.edoatley.co.uk"

  sub_domain {
    branch_name = aws_amplify_branch.main.branch_name
    prefix      = ""
  }
}
