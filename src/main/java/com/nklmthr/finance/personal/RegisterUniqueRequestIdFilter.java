package com.nklmthr.finance.personal;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

@Component
public class RegisterUniqueRequestIdFilter implements Filter{
	private static final String HEADER_REQUEST_ID = "X-Request-Id";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;

        // Get existing requestId from header or generate new
        String requestId = httpRequest.getHeader(HEADER_REQUEST_ID);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        // Store in ThreadLocal
        RequestContext.setRequestId(requestId);

        // Also add to MDC so it shows up in logs automatically
        MDC.put("requestId", requestId);

        try {
            chain.doFilter(request, response);
        } finally {
            RequestContext.clear();
            MDC.remove("requestId");
        }
    }
}
