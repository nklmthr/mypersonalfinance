package com.nklmthr.finance.personal.model;

import java.math.BigDecimal;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "account_types")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountType {

	@Id
	@UuidGenerator
	@Column
	private String id;

	@Column(nullable = false, unique = true)
	private String name;

	private String description;

	private String classification;

	@Transient
	private BigDecimal accountTypeBalance;

	@ManyToOne(optional = false)
	private AppUser appUser;
}
