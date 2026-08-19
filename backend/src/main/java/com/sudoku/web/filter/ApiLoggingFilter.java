package com.sudoku.web.filter;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * @spec UM-BE-050, UM-BE-051, UM-BE-052
 */
@Provider
public class ApiLoggingFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final Logger LOG = Logger.getLogger(ApiLoggingFilter.class);

    @ConfigProperty(name = "sudoku.api.logging.enabled", defaultValue = "false")
    boolean loggingEnabled;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        if (!loggingEnabled) return;

        String method = requestContext.getMethod();
        String path = requestContext.getUriInfo().getRequestUri().toString();

        if (requestContext.hasEntity()) {
            byte[] body = requestContext.getEntityStream().readAllBytes();
            requestContext.setEntityStream(new ByteArrayInputStream(body));
            LOG.infof(">> %s %s — body: %s", method, path, new String(body, StandardCharsets.UTF_8));
        } else {
            LOG.infof(">> %s %s", method, path);
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        if (!loggingEnabled) return;

        String method = requestContext.getMethod();
        String path = requestContext.getUriInfo().getRequestUri().toString();
        int status = responseContext.getStatus();
        Object entity = responseContext.getEntity();

        if (entity != null) {
            LOG.infof("<< %s %s — %d — body: %s", method, path, status, entity);
        } else {
            LOG.infof("<< %s %s — %d", method, path, status);
        }
    }
}
