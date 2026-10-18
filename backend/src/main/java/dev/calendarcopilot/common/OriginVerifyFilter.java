package dev.calendarcopilot.common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * In AWS, CloudFront adds a secret X-Origin-Verify header to every request it forwards.
 * The EC2 security group already only admits CloudFront's IP ranges; this check also
 * blocks requests routed through someone else's CloudFront distribution.
 * Disabled when no secret is configured (local development and tests).
 */
@Component
public class OriginVerifyFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Origin-Verify";

    private final byte[] secret;

    public OriginVerifyFilter(@Value("${app.origin-verify-secret:}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return secret.length == 0 || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        // Constant-time comparison so the secret cannot be guessed byte by byte from timing.
        if (provided == null || !MessageDigest.isEqual(secret, provided.getBytes(StandardCharsets.UTF_8))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        chain.doFilter(request, response);
    }
}
