package com.nklmthr.finance.personal.model;

import org.hibernate.annotations.UuidGenerator;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "categories")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Category {

	@Id
	@UuidGenerator
	@Column
	private String id;

	@Column(nullable = false)
	private String name;

	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "parent_id")
	private Category parent;

	@Column(name = "system_category", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
	private boolean systemCategory = false;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "app_user_id", nullable = false)
	@JsonIgnore
	private AppUser appUser;
}
