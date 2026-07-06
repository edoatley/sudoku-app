package com.sudoku.cors;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * JAX-RS provider that enforces CORS (Cross-Origin Resource Sharing) for the Sudoku API.
 *
 * <p>This filter is necessary because the frontend (React/Vite on AWS Amplify) is hosted on a
 * different origin than the backend API (AWS API Gateway + Lambda). Without explicit CORS headers,
 * browsers block cross-origin requests as a security measure.
 *
 * <p>Two filter phases are implemented:
 * <ul>
 *   <li><b>Request filter</b> — short-circuits HTTP {@code OPTIONS} preflight requests by
 *       immediately returning {@code 200 OK} with the required CORS headers, avoiding unnecessary
 *       Lambda invocation for the actual handler logic.</li>
 *   <li><b>Response filter</b> — attaches {@code Access-Control-Allow-*} headers to every
 *       non-preflight response so the browser permits the calling page to read the result.</li>
 * </ul>
 *
 * <p>Allowed origins are driven by the {@code sudoku.cors.allowed-origins} config property so that
 * local development, staging, and production environments can each whitelist only their own
 * frontend URL without code changes.
 */
@Provider
public class CorsFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final String ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin";
    private static final String ACCESS_CONTROL_ALLOW_METHODS = "Access-Control-Allow-Methods";
    private static final String GET_POST_PATCH_OPTIONS = "GET,POST,PATCH,OPTIONS";
    private static final String ACCESS_CONTROL_ALLOW_HEADERS = "Access-Control-Allow-Headers";
    private static final String CONTENT_TYPE_ACCEPT_AUTHORIZATION = "Content-Type,Accept,Authorization";

    @ConfigProperty(name = "sudoku.cors.allowed-origins")
    List<String> allowedOrigins;

    /**
     * Handles HTTP {@code OPTIONS} preflight requests by returning a {@code 200 OK} response with
     * the required CORS headers if the request's {@code Origin} is in the allowlist.
     *
     * <p>For non-preflight requests, this method does nothing and allows the request to proceed to
     * the next filter or resource method.
     *
     * @param requestContext the container request context
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(requestContext.getMethod())) {
            String origin = requestContext.getHeaderString("Origin");
            if (isAllowed(origin)) {
                requestContext.abortWith(
                    Response.ok()
                        .header(ACCESS_CONTROL_ALLOW_ORIGIN, origin)
                        .header(ACCESS_CONTROL_ALLOW_METHODS, GET_POST_PATCH_OPTIONS)
                        .header(ACCESS_CONTROL_ALLOW_HEADERS, CONTENT_TYPE_ACCEPT_AUTHORIZATION)
                        .build()
                );
            }
        }
    }

    /**
     * Adds CORS headers to the response for non-preflight requests if the request's {@code Origin}
     * is in the allowlist.
     *
     * @param requestContext the container request context
     * @param responseContext the container response context
     */
    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        String origin = requestContext.getHeaderString("Origin");
        if (isAllowed(origin)) {
            responseContext.getHeaders().putSingle(ACCESS_CONTROL_ALLOW_ORIGIN, origin);
            responseContext.getHeaders().putSingle(ACCESS_CONTROL_ALLOW_METHODS, "GET,POST,PATCH,OPTIONS");
            responseContext.getHeaders().putSingle(ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type,Accept,Authorization");
        }
    }

    private boolean isAllowed(String origin) {
        return origin != null && allowedOrigins.contains(origin);
    }

}
