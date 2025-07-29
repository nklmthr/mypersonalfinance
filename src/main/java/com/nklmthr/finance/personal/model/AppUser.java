package com.nklmthr.finance.personal.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.UuidGenerator;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class AppUser {
	@Id
	@UuidGenerator
	@Column
	private String id;

	@Column(unique = true, nullable = false)
	private String username;

	@JsonIgnore
	@Column(nullable = false)
	private String password; // BCrypt hashed

	@Column(nullable = false)
	@JsonIgnore
	private String role; // e.g., USER, ADMIN
	
	@JsonIgnore
	@Column(nullable = false)
	private String email;
	
	@Builder.Default
	private boolean enabled = true;
	
	@Column(length = 4096)
	@JsonIgnore
	private String gmailAccessToken;

	@Column(length = 4096)
	@JsonIgnore
	private String gmailRefreshToken;

	@JsonIgnore
	@Column
	private Long gmailTokenExpiry; 

	@JsonIgnore
	@Column
	private LocalDateTime createdAt;

}
