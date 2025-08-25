package com.nklmthr.finance.personal.security;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Autowired
	private UserService userService;

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
	    http
	        .cors(Customizer.withDefaults())
	        .csrf(csrf -> csrf.disable())  // Disable CSRF for APIs (use with caution)
	        .authorizeHttpRequests(auth -> auth
	            .requestMatchers(
	                "/api/auth/login/**",
	                "/oauth/authorize",
	                "/oauth/callback",
	                "/signup",
	                "/login",
	                "/",
	                "/index.html",
	                "/static/**",
	                "/css/**",
	                "/js/**",
	                "/images/**"
	            ).permitAll()
	            .requestMatchers("/api/**").authenticated()
	            .anyRequest().authenticated()
	        )
	        .exceptionHandling(exception -> exception
	            .authenticationEntryPoint((request, response, authException) -> {
	                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
	            })
	        )
	        .logout(logout -> logout.logoutUrl("/logout").permitAll())
	        .httpBasic(withDefaults()); // Optional: enable HTTP Basic auth for testing/dev

	    // Note: No formLogin() here, because login is handled via your REST controller

	    return http.build();
	}

	@Bean
	public AuthenticationManager authManager(HttpSecurity http) throws Exception {
		return http.getSharedObject(AuthenticationManagerBuilder.class).userDetailsService(userService)
				.passwordEncoder(passwordEncoder()).and().build();
	}

	public static void main(String[] args) {
		SecurityConfig s = new SecurityConfig();
		System.out.println(s.passwordEncoder().encode("P@ssword"));
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
