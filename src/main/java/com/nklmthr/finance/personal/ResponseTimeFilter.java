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

	private static final Logger log = LoggerFactory.getLogger(ResponseTimeFilter.class);

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		long start = System.currentTimeMillis();
		try {
			chain.doFilter(request, response);
		} finally {
			long duration = System.currentTimeMillis() - start;
			if (request instanceof HttpServletRequest httpReq) {
				log.info("Request [{} {}] completed in {} ms", httpReq.getMethod(), httpReq.getRequestURI(), duration);
			}
		}
	}

}
