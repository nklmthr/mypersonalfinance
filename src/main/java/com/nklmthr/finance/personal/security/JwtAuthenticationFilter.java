package com.nklmthr.finance.personal.security;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	@Autowired
	private JwtUtil jwtUtil;

	@Autowired
	private UserService userService;

	private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
	
	private static final AntPathMatcher pathMatcher = new AntPathMatcher();
	
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {

		String path = request.getServletPath();
		
		boolean whitelisted = SecurityConstants.WHITELIST.stream()
	            .anyMatch(pattern -> pathMatcher.match(pattern, path));

	    if (whitelisted) {
	        chain.doFilter(request, response);
	        return;
	    }
		logger.info("Processing request for path not in whitelist: {}", path);
		final String authHeader = request.getHeader("Authorization");
		String username = null;
		String jwt = null;

		logger.debug("Raw Authorization header: {}", authHeader);

		if (authHeader != null && authHeader.startsWith("Bearer ")) {
			jwt = authHeader.substring(7);
			try {
				username = jwtUtil.extractUsername(jwt);
			} catch (Exception e) {
				logger.warn("Failed to extract username from token: {}", e.getMessage());
			}
		}

		if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
			UserDetails userDetails = userService.loadUserByUsername(username);

			if (jwtUtil.validateToken(jwt, userDetails)) {
				UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(userDetails,
						null, userDetails.getAuthorities());
				authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
				SecurityContextHolder.getContext().setAuthentication(authToken);

				if (jwtUtil.shouldRefreshToken(jwt)) {
					String newToken = jwtUtil.generateToken(username);
					logger.debug("Issuing sliding refresh token for user {}", username);
					response.setHeader("X-Auth-Token", newToken);
				}

			} else {
				logger.warn("Invalid JWT token for user {}", username);
			}
		}

		chain.doFilter(request, response);
	}
}
