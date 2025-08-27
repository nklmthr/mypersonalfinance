package com.nklmthr.finance.personal.security;

import java.security.Key;
import java.util.Base64;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtUtil {
	@Value("${jwt.secret}") 
	private String secret;

	@Value("${jwt.expiration:300000}")
	private long jwtExpiration;

	private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);

	private Key getSigningKey() {
		byte[] keyBytes = Decoders.BASE64.decode(secret);
		Key key = Keys.hmacShaKeyFor(keyBytes);
		logger.debug("Using JWT signing key: {}", Base64.getEncoder().encodeToString(key.getEncoded()));
		return key;
	}

	public String generateToken(String username) {
		return Jwts.builder().setSubject(username).setIssuedAt(new Date())
				.setExpiration(new Date(System.currentTimeMillis() + jwtExpiration)).signWith(getSigningKey())
				.compact();
	}

	public String extractUsername(String token) {
		return extractAllClaims(token).getSubject();
	}

	public boolean validateToken(String token, UserDetails user) {
		final String username = extractUsername(token);
		Date expiration = extractAllClaims(token).getExpiration();
		logger.debug("Token expiration date: {}", expiration);
		return (username.equals(user.getUsername()) && expiration.after(new Date()));
	}

	private Claims extractAllClaims(String token) {
		return Jwts.parserBuilder().setSigningKey(getSigningKey()).build().parseClaimsJws(token).getBody();
	}
}
