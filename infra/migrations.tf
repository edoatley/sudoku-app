# =============================================================================
# migrations.tf — Terraform moved{} blocks for zero-destroy module migration
# =============================================================================
# Each block tells Terraform to treat an existing state entry at its old
# bespoke address as if it now lives at the new module-based address.
#
# IMPORTANT: Confirm the exact internal module resource addresses before
# applying. After `terraform init`, run:
#   terraform state list | grep <resource_type>
# If addresses differ from the targets below, update them before applying.
#
# Note on API Gateway integrations: the bespoke config had two shared
# integrations (one per Lambda). The apigateway-v2 module creates one
# integration per route. There is no 1:1 mapping, so the old integrations
# will be destroyed and new per-route integrations created. This is safe —
# integrations carry no persistent state and re-creation causes zero downtime.
#
# Once a migration has been applied to ALL workspaces (rc-iac-updates,
# rc-shared, default), the moved{} blocks for that phase can be removed.
# =============================================================================

# ── Phase 1a: Main Lambda ─────────────────────────────────────────────────────
# aws_lambda_alias.live and aws_lambda_permission.api_gateway stay at their
# existing bespoke addresses — the module (v7) does not manage aliases, and the
# permission must target the alias qualifier. No moved{} blocks needed for them.

moved {
  from = aws_lambda_function.sudoku
  to   = module.lambda.aws_lambda_function.this[0]
}

# ── Phase 1b: Image recognition Lambda ───────────────────────────────────────
# aws_lambda_permission.image_recognition_api_gateway stays at its bespoke
# address — no moved{} block needed.

moved {
  from = aws_lambda_function.image_recognition
  to   = module.image_recognition_lambda.aws_lambda_function.this[0]
}

moved {
  from = aws_cloudwatch_log_group.image_recognition_lambda
  to   = module.image_recognition_lambda.aws_cloudwatch_log_group.lambda[0]
}

# ── Phase 2: API Gateway ──────────────────────────────────────────────────────
# aws_apigatewayv2_integration.lambda and aws_apigatewayv2_integration.image_recognition
# cannot be moved: the bespoke config had 2 shared integrations; the module creates
# one per route (9 total). Old integrations are destroyed, new ones created — safe,
# no downtime, integrations carry no persistent state.

moved {
  from = aws_apigatewayv2_api.sudoku
  to   = module.api_gateway.aws_apigatewayv2_api.this[0]
}

moved {
  from = aws_apigatewayv2_stage.default
  to   = module.api_gateway.aws_apigatewayv2_stage.this[0]
}

moved {
  from = aws_apigatewayv2_authorizer.cognito_jwt
  to   = module.api_gateway.aws_apigatewayv2_authorizer.this["cognito_jwt"]
}

moved {
  from = aws_apigatewayv2_route.default
  to   = module.api_gateway.aws_apigatewayv2_route.this["$default"]
}

moved {
  from = aws_apigatewayv2_route.post_games
  to   = module.api_gateway.aws_apigatewayv2_route.this["POST /api/v1/games"]
}

moved {
  from = aws_apigatewayv2_route.post_games_from_image
  to   = module.api_gateway.aws_apigatewayv2_route.this["POST /api/v1/games/from-image"]
}

moved {
  from = aws_apigatewayv2_route.get_game
  to   = module.api_gateway.aws_apigatewayv2_route.this["GET /api/v1/games/{gameId}"]
}

moved {
  from = aws_apigatewayv2_route.patch_game
  to   = module.api_gateway.aws_apigatewayv2_route.this["PATCH /api/v1/games/{gameId}"]
}

moved {
  from = aws_apigatewayv2_route.get_game_current
  to   = module.api_gateway.aws_apigatewayv2_route.this["GET /api/v1/games/current"]
}

moved {
  from = aws_apigatewayv2_route.get_player_me
  to   = module.api_gateway.aws_apigatewayv2_route.this["GET /api/v1/players/me"]
}

moved {
  from = aws_apigatewayv2_route.post_puzzles_import
  to   = module.api_gateway.aws_apigatewayv2_route.this["POST /api/v1/ai/scan"]
}

# Workspaces already at the intermediate module address after Phase 2 migration
moved {
  from = module.api_gateway.aws_apigatewayv2_route.this["POST /api/v1/puzzles/import"]
  to   = module.api_gateway.aws_apigatewayv2_route.this["POST /api/v1/ai/scan"]
}

moved {
  from = aws_apigatewayv2_route.get_puzzles_import_warmup
  to   = module.api_gateway.aws_apigatewayv2_route.this["GET /api/v1/ai/scan/warmup"]
}

# Workspaces already at the intermediate module address after Phase 2 migration
moved {
  from = module.api_gateway.aws_apigatewayv2_route.this["GET /api/v1/puzzles/import/warmup"]
  to   = module.api_gateway.aws_apigatewayv2_route.this["GET /api/v1/ai/scan/warmup"]
}
