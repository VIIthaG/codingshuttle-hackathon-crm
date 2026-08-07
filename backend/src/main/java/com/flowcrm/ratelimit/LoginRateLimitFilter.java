package com.flowcrm.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowcrm.common.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rate-limits {@code POST /api/v1/auth/login} using {@link HttpServletRequest#getRemoteAddr()}.
 * Does not trust {@code X-Forwarded-For} by default.
 *
 * <p>Registered as a {@code @Bean} from {@link LoginRateLimitConfig}.
 */
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/api/v1/auth/login";

    private final LoginRateLimiter loginRateLimiter;
    private final ObjectMapper objectMapper;

    public LoginRateLimitFilter(LoginRateLimiter loginRateLimiter, ObjectMapper objectMapper) {
        this.loginRateLimiter = loginRateLimiter;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !(HttpMethod.POST.matches(request.getMethod()) && LOGIN_PATH.equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String clientKey = Optional.ofNullable(request.getRemoteAddr()).orElse("unknown");
        RateLimitDecision decision = loginRateLimiter.check(clientKey);
        if (!decision.allowed()) {
            response.setStatus(429);
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ErrorResponse body = new ErrorResponse(
                    Instant.now(),
                    429,
                    "Too Many Requests",
                    "Rate limit exceeded",
                    null);
            objectMapper.writeValue(response.getOutputStream(), body);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
