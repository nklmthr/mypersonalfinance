package com.nklmthr.finance.personal.model;

import java.math.BigDecimal;

import org.hibernate.annotations.UuidGenerator;

import com.fasterxml.jackson.annotation.JsonIgnore;

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
@Table(name = "accounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Account {

	@Id
	@UuidGenerator
	@Column
	private String id;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private BigDecimal balance;

	@ManyToOne(optional = false, fetch = FetchType.EAGER)
	@JoinColumn(name = "institution_id", nullable = false)
	private Institution institution;

	@ManyToOne(optional = false, fetch = FetchType.EAGER)
	@JoinColumn(name = "account_type_id", nullable = false)
	private AccountType accountType;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "app_user_id", nullable = false)
	@JsonIgnore
	private AppUser appUser;

}
