package com.nklmthr.finance.personal;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

@Component
public class RegisterUniqueRequestIdFilter implements Filter {
	private static final String HEADER_REQUEST_ID = "X-Request-Id";
	private static final Logger logger = LoggerFactory.getLogger(RegisterUniqueRequestIdFilter.class);

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {

		if (request instanceof HttpServletRequest) {
			HttpServletRequest httpRequest = (HttpServletRequest) request;
			String path = httpRequest.getRequestURI();
			if(path.contains("/static/")) {
				chain.doFilter(request, response);
				return;
			}
			String requestId = httpRequest.getHeader(HEADER_REQUEST_ID);
			if (requestId == null || requestId.isBlank()) {
				requestId = UUID.randomUUID().toString();
			}
			RequestContext.setRequestId(requestId);
			MDC.put("requestId", requestId);

			try {
				chain.doFilter(request, response); 
			} finally {
				RequestContext.clear();
				MDC.remove("requestId");
			}
		} else {
			chain.doFilter(request, response);
		}
	}
}
