package com.externconnector.sync.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Rejects webhook payloads exceeding the configured size limit.
 * Prevents memory exhaustion from maliciously large payloads.
 */
public class PayloadSizeLimitFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(PayloadSizeLimitFilter.class);

    private final long maxBytes;

    public PayloadSizeLimitFilter(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        int contentLength = httpRequest.getContentLength();
        if (contentLength > maxBytes) {
            log.warn("Rejected oversized webhook payload: {} bytes (max: {})", contentLength, maxBytes);
            httpResponse.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "Payload too large. Max: " + maxBytes + " bytes");
            return;
        }

        chain.doFilter(request, response);
    }
}
