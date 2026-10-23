package com.galeri.app.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final int MAX_BODY_LOG_LENGTH = 1000;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain chain)
            throws ServletException, IOException {

        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        MDC.put("requestId", requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request, MAX_BODY_LOG_LENGTH);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        long start = System.currentTimeMillis();
        try {
            chain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            long durationMs = System.currentTimeMillis() - start;
            int status = wrappedResponse.getStatus();
            String line = "%s %s -> %d (%d ms)".formatted(
                    request.getMethod(), requestUri(request), status, durationMs);

            if (status >= 500) {
                log.error(line);
            } else if (status >= 400) {
                log.warn(line);
            } else {
                log.info(line);
            }

            if (log.isDebugEnabled() && !isSensitivePath(request)) {
                log.debug("Request body: {}", bodyAsString(wrappedRequest.getContentAsByteArray(), wrappedRequest.getContentType()));
                log.debug("Response body: {}", bodyAsString(wrappedResponse.getContentAsByteArray(), wrappedResponse.getContentType()));
            }

            wrappedResponse.copyBodyToResponse();
            MDC.remove("requestId");
            MDC.remove("tenantId");
            MDC.remove("userId");
        }
    }

    private boolean isSensitivePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/api/auth");
    }

    private String requestUri(HttpServletRequest request) {
        String query = request.getQueryString();
        return query == null ? request.getRequestURI() : request.getRequestURI() + "?" + query;
    }

    private String bodyAsString(byte[] content, String contentType) {
        if (content == null || content.length == 0) {
            return "";
        }
        if (contentType == null || !(contentType.contains("json") || contentType.contains("text"))) {
            return "[loglanmadi: %s]".formatted(contentType);
        }
        String body = new String(content, StandardCharsets.UTF_8);
        if (body.length() > MAX_BODY_LOG_LENGTH) {
            return body.substring(0, MAX_BODY_LOG_LENGTH) + "...(kesildi)";
        }
        return body;
    }
}
