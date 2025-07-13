package com.nklmthr.finance.personal.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.UuidGenerator;

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

	@Column(nullable = false)
	private String password; // BCrypt hashed

	@Column(nullable = false)
	private String role; // e.g., USER, ADMIN
	
	@Column(nullable = false)
	private String email;
	
	@Builder.Default
	private boolean enabled = true;

	// Optional: audit fields
	private LocalDateTime createdAt;

}
