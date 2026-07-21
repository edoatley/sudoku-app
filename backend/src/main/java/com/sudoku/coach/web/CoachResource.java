package com.sudoku.coach.web;

import com.sudoku.auth.UserIdentityResolver;
import com.sudoku.coach.bedrock.CoachRateLimiter;
import com.sudoku.coach.SudokuCoachService;
import io.quarkus.security.Authenticated;
import com.sudoku.player.web.PlayerProfile;
import com.sudoku.player.PlayerRepository;
import com.sudoku.player.PlayerService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.YearMonth;
import java.util.Map;

// @spec SC-API-001, SC-API-002, SC-API-003, SC-API-004, SC-API-010, SC-API-011, SC-API-012
// @spec SC-RL-001, SC-RL-002, SC-RL-003, SC-RL-004

/**
 * REST entry point for the AI coaching endpoint.
 *
 * <p>Requires a valid JWT (validated in-app; also enforced by API Gateway on AWS).
 * Enforces per-user guardrails: coach toggle, monthly token budget, and per-minute rate limit.
 */
@Path("/ai/coach")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CoachResource {

    @Inject
    SudokuCoachService coachService;

    @Inject
    PlayerService playerService;

    @Inject
    PlayerRepository playerRepository;

    @Inject
    CoachRateLimiter rateLimiter;

    @Inject
    UserIdentityResolver userIdentityResolver;

    @ConfigProperty(name = "coach.monthly-token-limit")
    long monthlyTokenLimit;

    @POST
    public Response coach(CoachRequest request) {
        // @spec SC-API-003 — board shape/digit validation delegated to Board.fromGrid()
        //                     via InvalidGridExceptionMapper → 400
        if (request == null || request.userMessage() == null || request.userMessage().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        String userId = userIdentityResolver.resolveUserId();
        PlayerProfile player = playerService.getOrCreateProfile(userId, null, null);

        // @spec SC-RL-001 — coach disabled check
        if (Boolean.FALSE.equals(player.aiCoachEnabled())) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(Map.of("error", "AI Coach is disabled. Enable it in your profile settings."))
                    .build();
        }

        // @spec SC-RL-002, SC-RL-005 — monthly token budget check
        // Soft limit: usedThisMonth is read from the profile snapshot above, before the Bedrock call.
        // Under concurrent load near the limit, two requests can both pass with the same stale count,
        // both call Bedrock, and both increment — exceeding the limit by at most one call's tokens
        // (~300 tokens, <$0.001 for Claude Haiku). Pre-reservation is not feasible because token cost
        // is unknown before invoking Bedrock. This soft-limit behaviour is intentional and accepted.
        String currentMonth = YearMonth.now().toString();
        long usedThisMonth = currentMonth.equals(player.coachTokenMonth())
                ? (player.coachTokensUsedThisMonth() != null ? player.coachTokensUsedThisMonth() : 0L)
                : 0L;
        if (usedThisMonth >= monthlyTokenLimit) {
            YearMonth nextMonth = YearMonth.now().plusMonths(1);
            return Response.status(429)
                    .entity(Map.of(
                            "error", "Monthly AI Coach quota exceeded.",
                            "tokensUsed", usedThisMonth,
                            "monthlyLimit", monthlyTokenLimit,
                            "resetsAt", nextMonth.atDay(1).toString()
                    ))
                    .build();
        }

        // @spec SC-RL-003, SC-RL-004 — per-minute rate limit
        if (!rateLimiter.tryConsume(userId)) {
            return Response.status(429)
                    .header("Retry-After", rateLimiter.secondsUntilNextWindow())
                    .entity(Map.of("error", "Rate limit exceeded. Try again in a moment."))
                    .build();
        }

        return switch (coachService.coach(request)) {
            // @spec SC-RL-010 — stamp the post-call cumulative total onto the response so the
            // frontend can update its token counter without a separate profile fetch.
            case SudokuCoachService.CoachResult.Response r -> {
                long tokensUsedThisMonth = usedThisMonth;
                if (r.tokensUsed() > 0) {
                    playerRepository.incrementCoachTokens(userId, r.tokensUsed(), currentMonth);
                    tokensUsedThisMonth += r.tokensUsed();
                }
                CoachResponse withTotal = new CoachResponse(
                        r.coachResponse().aiMessage(), r.coachResponse().hint(),
                        r.coachResponse().revealHint(), tokensUsedThisMonth);
                yield Response.ok(withTotal).build();
            }
            case SudokuCoachService.CoachResult.PuzzleSolved ignored -> Response.noContent().build();
        };
    }
}
