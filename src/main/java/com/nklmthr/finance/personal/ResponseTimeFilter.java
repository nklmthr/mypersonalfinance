package com.nklmthr.finance.personal;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

@Component
public class ResponseTimeFilter implements Filter {

	private static final Logger logger = LoggerFactory.getLogger(ResponseTimeFilter.class);

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {

		if (request instanceof HttpServletRequest httpReq) {
			String path = httpReq.getRequestURI();
			logger.info("Filtering request for path: {}", path);
			boolean isStatic = path.contains("/static/") || path.contains("/js/") || path.contains("/css/")
					|| path.contains("/images/") || path.contains("/gmail");
			long start = 0;

			if (isStatic) {
				start = System.currentTimeMillis();
			}
			try {
				chain.doFilter(request, response);
			} finally {
				if (isStatic) {
					long duration = System.currentTimeMillis() - start;
					logger.info("Request [{} {}] completed in {} ms", httpReq.getMethod(), path, duration);
				}
			}
		} else {
			chain.doFilter(request, response);
		}
	}

}
