package com.nklmthr.finance.personal.model;

import java.math.BigDecimal;

import org.hibernate.annotations.UuidGenerator;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

	@ManyToOne(optional = false)
	@JoinColumn(name = "institution_id", nullable = false)
	private Institution institution;

	@ManyToOne(optional = false)
	@JoinColumn(name = "account_type_id", nullable = false)
	private AccountType accountType;

	@ManyToOne(optional = false)
	@JoinColumn(name = "app_user_id", nullable = false)
	@JsonIgnore
	private AppUser appUser;

	// Account identification attributes for transaction text matching
	@Column(name = "account_number", length = 100)
	private String accountNumber;

	@Column(name = "account_keywords", length = 500)
	private String accountKeywords; // Comma-separated keywords that might appear in transaction text

	@Column(name = "account_aliases", length = 300)
	private String accountAliases; // Comma-separated alternative names/abbreviations


}
