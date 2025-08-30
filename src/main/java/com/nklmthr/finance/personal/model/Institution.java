package com.nklmthr.finance.personal.model;

import org.hibernate.annotations.UuidGenerator;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "institutions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Institution {

	@Id
	@UuidGenerator
	@Column
	private String id;

	@Column(nullable = false, unique = true)
	private String name;

	@Column(length = 1000)
	private String description;

	@ManyToOne(optional = false)
	@JsonIgnore
	private AppUser appUser;

}
